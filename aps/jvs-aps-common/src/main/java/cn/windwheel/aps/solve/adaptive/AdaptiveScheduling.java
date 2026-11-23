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
                        String score, String status, long elapsedMillis) {}
    public record Report(String algorithm, long rootSeed, int selectedTrial, String stopReason,
                         List<Trial> trials) {}
    private record Member(double[] vector, double f, double cr, SchedulingSolution result) {}

    public static SchedulingSolution solve(SchedulingSolution input, BooleanSupplier cancelled,
                                           Consumer<Report> audit) throws InterruptedException {
        SolveManageConfigurator.validate(input.getStrategy().getConfig());
        var settings = input.getStrategy().getConfig().getAdaptive();
        long rootSeed = Optional.ofNullable(input.getStrategy().getConfig().getRandomSeed()).orElse(17L);
        Random random = new Random(rootSeed);
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(settings.getTotalSeconds());
        var mapper = SolveProblemSnapshot.mapper(new ObjectMapper().findAndRegisterModules());
        final byte[] snapshot;
        try { snapshot = mapper.writeValueAsBytes(SolveProblemSnapshot.from(input)); }
        catch (Exception ex) { throw new IllegalArgumentException("无法创建独立求解快照", ex); }
        List<Trial> trials = new ArrayList<>();
        List<Member> population = new ArrayList<>();
        SchedulingSolution best = null;
        int selected = -1;
        String stop = "COMPLETED";
        for (int generation = 0; generation <= settings.getGenerations(); generation++) {
            // Synchronous generations prevent evaluation order from changing donor selection.
            List<Member> parents = new ArrayList<>(population);
            for (int i = 0; i < settings.getPopulationSize(); i++) {
                if (Thread.currentThread().isInterrupted()) throw new InterruptedException();
                if (cancelled != null && cancelled.getAsBoolean()) {
                    audit.accept(new Report("JDE_TIMEFOLD_V1",rootSeed,selected,"CANCELLED",List.copyOf(trials)));
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
                SchedulingSolution problem;
                try { problem = mapper.readValue(snapshot,SolveProblemSnapshot.class).restore(); }
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
                long nanos = Math.min(TimeUnit.SECONDS.toNanos(settings.getTrialSeconds()),deadline-System.nanoTime());
                if (nanos < TimeUnit.MILLISECONDS.toNanos(1)) { stop="TIME_BUDGET"; break; }
                var solverConfig = new TimefoldManageConfigurator().buildConfig(config);
                solverConfig.setTerminationConfig(new TerminationConfig()
                        .withSpentLimit(Duration.ofMillis(TimeUnit.NANOSECONDS.toMillis(nanos)))
                        .withScoreCalculationCountLimit(settings.getTrialScoreLimit()));
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
                    audit.accept(new Report("JDE_TIMEFOLD_V1",rootSeed,selected,"CANCELLED",List.copyOf(trials)));
                    return null;
                }
                // Independent full score update, followed by immutable frozen-baseline comparison.
                var score = SolutionManager.create(factory).update(result, SolutionUpdatePolicy.UPDATE_SCORE_ONLY);
                boolean feasible = score != null && score.initScore()==0 && score.isFeasible()
                        && frozenPreserved(input,result);
                int trialId = trials.size();
                trials.add(new Trial(trialId,generation,Arrays.stream(vector).boxed().toList(),f,cr,seed,
                        config.getSolverProfile().name(),config.getEntityTabuSize(),config.getLateAcceptanceSize(),
                        config.getAcceptedCountLimit(),String.valueOf(score),feasible?"FEASIBLE":"INFEASIBLE",
                        TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-started)));
                if (feasible) {
                    result.setScore(org.optaplanner.core.api.score.buildin.hardsoftlong.HardSoftLongScore
                            .of(result.getTimefoldScore().hardScore(),result.getTimefoldScore().softScore()));
                    if (better(result,best)) { best=result; selected=trialId; }
                } else result=null;
                Member member = new Member(vector,f,cr,result);
                if (generation==0) population.add(member);
                else if (better(result,parents.get(i).result())) population.set(i,member);
                // Persist before beginning another trial; failures propagate and prevent publication.
                audit.accept(new Report("JDE_TIMEFOLD_V1",rootSeed,selected,"RUNNING",List.copyOf(trials)));
            }
            if (!stop.equals("COMPLETED")) break;
        }
        audit.accept(new Report("JDE_TIMEFOLD_V1",rootSeed,selected,stop,List.copyOf(trials)));
        if (best==null) throw new IllegalStateException("自适应求解预算内未找到硬约束可行解；请检查物料、冻结和资源冲突");
        // Expose the actual winning inner strategy, with the original outer request in audit.inputJson.
        return best;
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
