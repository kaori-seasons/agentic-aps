package cn.windwheel.aps.solve.adaptive;

import ai.timefold.solver.core.api.solver.*;
import ai.timefold.solver.core.config.solver.termination.TerminationConfig;
import cn.windwheel.aps.entity.dto.planning.StrategyConfigDTO;
import cn.windwheel.aps.entity.enums.SolverProfileEnum;
import cn.windwheel.aps.solve.SolveProblemSnapshot;
import cn.windwheel.aps.solve.config.SolveManageConfigurator;
import cn.windwheel.aps.solve.model.*;
import cn.windwheel.aps.solve.score.SchedulingRules;
import cn.windwheel.aps.solve.timefold.TimefoldManageConfigurator;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.*;

/** Bounded jDE parameter search over isolated Timefold runs. All trials share one objective. */
public final class AdaptiveScheduling {
    private AdaptiveScheduling() {}

    public record Trial(int index, int generation, List<Double> vector, double f, double cr,
                        long seed, String profile, int tabuSize, int lateSize, int acceptedLimit,
                        String score, String status, long elapsedMillis, Execution execution) {}
    public record Execution(String startMode, int seedTrial, String seedScore, String fallbackReason,
                            boolean constructionEnabled, long budgetMillis, long scoreLimit,
                            long preparationMillis, long solveMillis, long validationMillis) {}
    public record Report(String algorithm, long rootSeed, int selectedTrial, String stopReason,
                         List<Trial> trials, AdaptiveBudgetPolicy.Plan budget, boolean warmStartEnabled) {
        public Report(String algorithm, long rootSeed, int selectedTrial, String stopReason, List<Trial> trials) {
            this(algorithm, rootSeed, selectedTrial, stopReason, trials, null, false);
        }
    }
    // Retain only fitness in the population, not N complete mutable planning graphs.
    private record Member(double[] vector, double f, double cr,
                          ai.timefold.solver.core.api.score.buildin.hardsoftlong.HardSoftLongScore score) {}

    public static SchedulingSolution solve(SchedulingSolution input, BooleanSupplier cancelled,
                                           Consumer<Report> audit) throws InterruptedException {
        SolveManageConfigurator.validate(input.getStrategy().getConfig());
        var settings = input.getStrategy().getConfig().getAdaptive();
        var budget = AdaptiveBudgetPolicy.plan(input, settings);
        long rootSeed = Optional.ofNullable(input.getStrategy().getConfig().getRandomSeed()).orElse(17L);
        Random random = new Random(rootSeed);
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(settings.getTotalSeconds());
        var mapper = SolveProblemSnapshot.mapper(new ObjectMapper().findAndRegisterModules());
        final byte[] snapshot;
        final byte[] originalConfig;
        try {
            snapshot = mapper.writeValueAsBytes(SolveProblemSnapshot.from(input));
            originalConfig = mapper.writeValueAsBytes(input.getStrategy().getConfig());
        }
        catch (Exception ex) { throw new IllegalArgumentException("无法创建独立求解快照", ex); }
        var validationManager = SolutionManager.create(SolverFactory.<SchedulingSolution>create(
                new TimefoldManageConfigurator().buildConfig(input.getStrategy().getConfig())));
        List<Trial> trials = new ArrayList<>();
        List<Member> population = new ArrayList<>();
        SchedulingSolution best = null;
        // Serialized only on improvement. Every trial restores a private graph, including business facts.
        byte[] bestSnapshot = null;
        int selected = -1;
        String stop = "COMPLETED";
        audit.accept(report(rootSeed, selected, "RUNNING", trials, budget, settings.isWarmStartEnabled()));
        for (int generation = 0; generation <= settings.getGenerations(); generation++) {
            // Synchronous generations prevent evaluation order from changing donor selection.
            List<Member> parents = new ArrayList<>(population);
            for (int i = 0; i < settings.getPopulationSize(); i++) {
                if (Thread.currentThread().isInterrupted()) throw new InterruptedException();
                if (cancelled != null && cancelled.getAsBoolean()) {
                    audit.accept(report(rootSeed,selected,"CANCELLED",trials,budget,settings.isWarmStartEnabled()));
                    return null;
                }
                if (System.nanoTime() >= deadline) { stop = "TIME_BUDGET"; break; }
                double f = .5, cr = .8;
                double[] vector;
                if (generation == 0) {
                    vector = new double[]{random.nextDouble(),random.nextDouble(),random.nextDouble(),random.nextDouble()};
                    // First candidate is the user's unmodified strategy: always retain its best result.
                } else {
                    Member target = parents.get(i);
                    f = random.nextDouble() < .1 ? .1 + .9 * random.nextDouble() : target.f();
                    cr = random.nextDouble() < .1 ? random.nextDouble() : target.cr();
                    List<Integer> donors = new ArrayList<>();
                    for (int j=0;j<parents.size();j++) if (j!=i) donors.add(j);
                    Collections.shuffle(donors,random);
                    vector = mutate(target.vector(), parents.get(donors.get(0)).vector(),
                            parents.get(donors.get(1)).vector(), parents.get(donors.get(2)).vector(),f,cr,random);
                }
                long preparedAt = System.nanoTime();
                boolean warm = settings.isWarmStartEnabled() && bestSnapshot != null;
                int seedTrial = warm ? selected : -1;
                String seedScore = warm ? best.getTimefoldScore().toString() : null;
                String fallback = null;
                SchedulingSolution problem;
                try {
                    problem = mapper.readValue(warm ? bestSnapshot : snapshot,SolveProblemSnapshot.class).restore();
                    // Search parameters must start from the request, not leak from the incumbent's strategy.
                    problem.getStrategy().setConfig(mapper.readValue(originalConfig, StrategyConfigDTO.class));
                }
                catch (Exception ex) { throw new IllegalStateException("求解快照无法重建",ex); }
                StrategyConfigDTO config = problem.getStrategy().getConfig();
                if (config.getSolverProfile()==null) config.setSolverProfile(SolverProfileEnum.DEFAULT);
                if (config.getEntityTabuSize()==null) config.setEntityTabuSize(7);
                if (config.getLateAcceptanceSize()==null) config.setLateAcceptanceSize(400);
                if (config.getAcceptedCountLimit()==null) config.setAcceptedCountLimit(1000);
                if (generation == 0 && i == 0) vector = encode(config);
                else decode(config,vector);
                long seed = rootSeed; // common random numbers: parameter comparisons use the same solver seed
                config.setRandomSeed(seed);
                var configurator = new TimefoldManageConfigurator();
                var solverConfig = configurator.buildConfig(config, warm);
                if (warm) {
                    // Rebuild shadows and re-score the copied seed before skipping construction.
                    boolean intact = completeQueues(problem) && frozenPreserved(input, problem);
                    var seedFitness = intact ? validationManager.update(problem, SolutionUpdatePolicy.UPDATE_ALL) : null;
                    if (seedFitness == null || seedFitness.initScore() != 0 || !seedFitness.isFeasible()
                            || !seedFitness.equals(best.getTimefoldScore()) || !frozenPreserved(input, problem)) {
                        fallback = "WARM_SEED_VALIDATION_FAILED";
                        warm = false;
                        try { problem = mapper.readValue(snapshot,SolveProblemSnapshot.class).restore(); }
                        catch (Exception ex) { throw new IllegalStateException("冷启动快照无法重建", ex); }
                        problem.getStrategy().setConfig(config);
                        solverConfig = configurator.buildConfig(config, false);
                    }
                }
                var allowance = budget.allocate(warm, TimeUnit.NANOSECONDS.toMillis(deadline-System.nanoTime()),
                        budget.plannedTrials()-trials.size());
                if (allowance.millis() < 1) { stop="TIME_BUDGET"; break; }
                solverConfig.setTerminationConfig(new TerminationConfig()
                        .withSpentLimit(Duration.ofMillis(allowance.millis()))
                        .withScoreCalculationCountLimit(allowance.scoreLimit()));
                var factory = SolverFactory.<SchedulingSolution>create(solverConfig);
                var solver = factory.buildSolver();
                long started = System.nanoTime();
                SchedulingSolution result;
                AtomicReference<RuntimeException> cancelFailure = new AtomicReference<>();
                var monitor = Executors.newSingleThreadScheduledExecutor(r -> {
                    Thread t = new Thread(r,"aps-adaptive-cancel"); t.setDaemon(true); return t;
                });
                try {
                    monitor.scheduleWithFixedDelay(() -> {
                        try {
                            if (System.nanoTime() >= deadline || (cancelled != null && cancelled.getAsBoolean()))
                                solver.terminateEarly();
                        } catch (RuntimeException ex) { cancelFailure.set(ex); solver.terminateEarly(); }
                    },0,50,TimeUnit.MILLISECONDS);
                    result = solver.solve(problem);
                } finally { monitor.shutdownNow(); }
                if (cancelFailure.get()!=null) throw new IllegalStateException("取消状态不可用，已停止求解",cancelFailure.get());
                if (Thread.currentThread().isInterrupted()) throw new InterruptedException();
                if (cancelled != null && cancelled.getAsBoolean()) {
                    audit.accept(report(rootSeed,selected,"CANCELLED",trials,budget,settings.isWarmStartEnabled()));
                    return null;
                }
                // Independent full score update, followed by immutable frozen-baseline comparison.
                long validationStarted = System.nanoTime();
                var score = validationManager.update(result, SolutionUpdatePolicy.UPDATE_SCORE_ONLY);
                boolean feasible = score != null && score.initScore()==0 && score.isFeasible()
                        && completeQueues(result) && frozenPreserved(input,result);
                int trialId = trials.size();
                trials.add(new Trial(trialId,generation,Arrays.stream(vector).boxed().toList(),f,cr,seed,
                        config.getSolverProfile().name(),config.getEntityTabuSize(),config.getLateAcceptanceSize(),
                        config.getAcceptedCountLimit(),String.valueOf(score),feasible?"FEASIBLE":"INFEASIBLE",
                        TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-preparedAt),
                        new Execution(warm ? "WARM" : "COLD",seedTrial,seedScore,fallback,!warm,
                                allowance.millis(),allowance.scoreLimit(),
                                TimeUnit.NANOSECONDS.toMillis(started-preparedAt),
                                TimeUnit.NANOSECONDS.toMillis(validationStarted-started),
                                TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-validationStarted))));
                if (feasible) {
                    result.setScore(org.optaplanner.core.api.score.buildin.hardsoftlong.HardSoftLongScore
                            .of(result.getTimefoldScore().hardScore(),result.getTimefoldScore().softScore()));
                    if (better(result,best)) {
                        best=result; selected=trialId;
                        if (settings.isWarmStartEnabled()) {
                            try { bestSnapshot = mapper.writeValueAsBytes(SolveProblemSnapshot.from(best)); }
                            catch (Exception ex) { throw new IllegalStateException("无法保存热启动快照", ex); }
                        }
                    }
                } else result=null;
                Member member = new Member(vector,f,cr,result == null ? null : result.getTimefoldScore());
                if (generation==0) population.add(member);
                else if (member.score()!=null && (parents.get(i).score()==null
                        || member.score().compareTo(parents.get(i).score())>0)) population.set(i,member);
                // Persist before beginning another trial; failures propagate and prevent publication.
                audit.accept(report(rootSeed,selected,"RUNNING",trials,budget,settings.isWarmStartEnabled()));
            }
            if (!stop.equals("COMPLETED")) break;
        }
        audit.accept(report(rootSeed,selected,stop,trials,budget,settings.isWarmStartEnabled()));
        if (best==null) throw new IllegalStateException("自适应求解预算内未找到硬约束可行解；请检查物料、冻结和资源冲突");
        // Expose the actual winning inner strategy, with the original outer request in audit.inputJson.
        return best;
    }

    private static Report report(long seed, int selected, String stop, List<Trial> trials,
                                 AdaptiveBudgetPolicy.Plan budget, boolean warm) {
        return new Report("JDE_TIMEFOLD_V2", seed, selected, stop, List.copyOf(trials), budget, warm);
    }

    /** Require exactly one assignment per active task and an intact frozen prefix on every resource. */
    public static boolean completeQueues(SchedulingSolution solution) {
        Set<ProductionTask> expected = Collections.newSetFromMap(new IdentityHashMap<>());
        solution.getTasks().stream().filter(SchedulingRules::active).forEach(expected::add);
        Set<ProductionTask> assigned = Collections.newSetFromMap(new IdentityHashMap<>());
        for (var resource : solution.getMainResources()) {
            var queue = resource.getTaskList();
            int pinned = resource.getFirstUnpinnedIndex();
            if (pinned < 0 || pinned > queue.size()) return false;
            for (int i=0;i<queue.size();i++) {
                var task = queue.get(i);
                if (!expected.contains(task) || !assigned.add(task) || task.getResource()!=resource
                        || !SchedulingRules.scheduled(task) || Boolean.TRUE.equals(task.getPinned()) != (i < pinned)) return false;
            }
        }
        return assigned.size() == expected.size();
    }

    static boolean better(SchedulingSolution candidate, SchedulingSolution incumbent) {
        return candidate!=null && (incumbent==null || candidate.getTimefoldScore().compareTo(incumbent.getTimefoldScore())>0);
    }

    public static boolean frozenPreserved(SchedulingSolution original, SchedulingSolution result) {
        Map<String,ProductionTask> byCode = new HashMap<>();
        for (var t : result.getTasks()) if (byCode.put(t.getCode(),t)!=null) return false;
        if (byCode.size()!=original.getTasks().size()) return false;
        for (var t : original.getTasks()) {
            var r = byCode.get(t.getCode());
            if (r==null) return false;
            if (Boolean.TRUE.equals(t.getPinned()) && (!Boolean.TRUE.equals(r.getPinned())
                    || !SchedulingRules.scheduled(t) || !SchedulingRules.scheduled(r)
                    || !Objects.equals(t.getResource().getId(),r.getResource().getId())
                    || !Objects.equals(t.getStartTime(),r.getStartTime()) || !Objects.equals(t.getEndTime(),r.getEndTime()))) return false;
        }
        return true;
    }

    /** DE/rand/1/bin with mandatory crossover coordinate and bounded projection. */
    public static double[] mutate(double[] target,double[] a,double[] b,double[] c,double f,double cr,Random random) {
        double[] result=target.clone();
        int forced=random.nextInt(result.length);
        for(int j=0;j<result.length;j++) if(j==forced || random.nextDouble()<cr)
            result[j]=Math.max(0,Math.min(1,a[j]+f*(b[j]-c[j])));
        return result;
    }

    private static double[] encode(StrategyConfigDTO c) {
        return new double[]{c.getSolverProfile()==SolverProfileEnum.FFD_LATE_ACCEPTANCE?1:0,
                clamp((c.getEntityTabuSize()-5)/75.0),clamp((c.getLateAcceptanceSize()-100)/1900.0),
                clamp((c.getAcceptedCountLimit()-200)/7800.0)};
    }
    private static double clamp(double x) { return Math.max(0,Math.min(1,x)); }
    private static void decode(StrategyConfigDTO c,double[] x) {
        c.setSolverProfile(x[0]<.5?SolverProfileEnum.FFD_TABU:SolverProfileEnum.FFD_LATE_ACCEPTANCE)
                .setEntityTabuSize(5+(int)Math.round(x[1]*75))
                .setLateAcceptanceSize(100+(int)Math.round(x[2]*1900))
                .setAcceptedCountLimit(200+(int)Math.round(x[3]*7800));
    }
}
