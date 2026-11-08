package cn.windwheel.aps.solve.engine;

import ai.timefold.solver.core.api.solver.SolverFactory;
import ai.timefold.solver.core.api.solver.SolverManager;
import ai.timefold.solver.core.config.constructionheuristic.ConstructionHeuristicPhaseConfig;
import ai.timefold.solver.core.config.localsearch.LocalSearchPhaseConfig;
import ai.timefold.solver.core.config.localsearch.decider.acceptor.LocalSearchAcceptorConfig;
import ai.timefold.solver.core.config.localsearch.decider.forager.LocalSearchForagerConfig;
import ai.timefold.solver.core.config.localsearch.decider.forager.FinalistPodiumType;
import ai.timefold.solver.core.config.solver.SolverConfig;
import ai.timefold.solver.core.config.solver.termination.TerminationConfig;
import ai.timefold.solver.core.config.solver.termination.TerminationCompositionStyle;
import cn.windwheel.aps.entity.dto.planning.StrategyConfigDTO;
import cn.windwheel.aps.entity.enums.SolverProfileEnum;
import cn.windwheel.aps.solve.config.SolverSettings;
import cn.windwheel.aps.solve.model.MainProductionResource;
import cn.windwheel.aps.solve.model.ProductionTask;
import cn.windwheel.aps.solve.model.SchedulingSolution;
import cn.windwheel.aps.solve.score.TimefoldConstraintProvider;
import cn.windwheel.common.exception.BusinessException;
import cn.windwheel.common.utils.ObjectNull;

import java.time.Duration;
import java.util.Comparator;
import java.util.UUID;
import java.util.concurrent.ExecutionException;

public final class TimefoldV1Engine implements PlanningSolverEngine {
    @Override
    public boolean supports(SolverProfileEnum profile) {
        return profile.isTimefold();
    }

    public SolverConfig buildSolverConfig(StrategyConfigDTO config) {
        SolverSettings.validate(config);
        if (!supports(SolverSettings.profile(config))) {
            throw new IllegalArgumentException("Legacy profile cannot use Timefold");
        }
        LocalSearchAcceptorConfig acceptor = new LocalSearchAcceptorConfig();
        LocalSearchForagerConfig forager = new LocalSearchForagerConfig()
                .withAcceptedCountLimit(SolverSettings.acceptedCount(config));
        switch (SolverSettings.profile(config)) {
            case TF_TABU -> acceptor.setValueTabuSize(SolverSettings.tabuSize(config));
            case TF_LATE_ACCEPTANCE -> acceptor.setLateAcceptanceSize(SolverSettings.lateAcceptanceSize(config));
            case TF_SIMULATED_ANNEALING -> acceptor.setSimulatedAnnealingStartingTemperature(SolverSettings.temperature(config));
            case TF_GREAT_DELUGE -> acceptor.setGreatDelugeWaterLevelIncrementRatio(SolverSettings.delugeIncrement(config).doubleValue());
            case TF_TABU_OSCILLATION -> {
                acceptor.setValueTabuSize(SolverSettings.tabuSize(config));
                forager.setFinalistPodiumType(FinalistPodiumType.STRATEGIC_OSCILLATION);
            }
            default -> throw new IllegalArgumentException("Unsupported Timefold profile");
        }
        long unimprovedSeconds = config.getMaxNoImprovementTime() == null || config.getMaxNoImprovementTime() == 0
                ? 30L : config.getMaxNoImprovementTime();
        SolverConfig solver = new SolverConfig()
                .withSolutionClass(SchedulingSolution.class)
                .withEntityClasses(MainProductionResource.class, ProductionTask.class)
                .withConstraintProviderClass(TimefoldConstraintProvider.class)
                // Community edition does not require enterprise move threading.
                .withMoveThreadCount("NONE")
                .withTerminationConfig(new TerminationConfig()
                        .withTerminationCompositionStyle(TerminationCompositionStyle.OR)
                        .withSpentLimit(Duration.ofSeconds(SolverSettings.maxSeconds(config)))
                        .withUnimprovedSecondsSpentLimit(unimprovedSeconds))
                // List variables use the list-compatible construction heuristic, not scalar FIRST_FIT_DECREASING.
                .withPhases(new ConstructionHeuristicPhaseConfig(), new LocalSearchPhaseConfig()
                        .withAcceptorConfig(acceptor).withForagerConfig(forager));
        solver.getScoreDirectorFactoryConfig().setConstraintProviderCustomProperties(SolverSettings.weightProperties(config));
        if (config.getRandomSeed() != null) {
            solver.setRandomSeed(config.getRandomSeed());
        }
        return solver;
    }

    @Override
    public ManagedSolverSession createSession(StrategyConfigDTO config) {
        SolverFactory<SchedulingSolution> factory = SolverFactory.create(buildSolverConfig(config));
        SolverManager<SchedulingSolution, UUID> manager = SolverManager.create(factory);
        return new ManagedSolverSession((id, problem) -> {
            preparePinnedTasks(problem);
            var job = manager.solve(id, problem);
            return new ManagedSolverSession.Job() {
                public SchedulingSolution getFinalBestSolution() throws InterruptedException, ExecutionException {
                    SchedulingSolution result = job.getFinalBestSolution();
                    var score = result.getTimefoldScore();
                    if (score != null) {
                        result.setScore(org.optaplanner.core.api.score.buildin.hardsoftlong.HardSoftLongScore
                                .ofUninitialized(score.initScore(), score.hardScore(), score.softScore()));
                    }
                    return result;
                }
                public boolean isTerminatedEarly() {
                    return job.isTerminatedEarly();
                }
            };
        }, manager::terminateEarly, manager::close);
    }

    public static void preparePinnedTasks(SchedulingSolution problem) {
        for (ProductionTask task : problem.getTasks()) {
            if (Boolean.TRUE.equals(task.getPinned()) && ObjectNull.isNull(task.getMergeTaskCode())
                    && (task.getResource() == null || task.getStartTime() == null
                    || !problem.getMainResources().contains(task.getResource())
                    || !task.getResource().getTaskList().contains(task))) {
                throw new BusinessException("锁定任务缺少有效的资源分配或开始时间：" + task.getCode());
            }
        }
        for (MainProductionResource resource : problem.getMainResources()) {
            // Keep the relative order of locked tasks and their business timestamps.
            resource.getTaskList().sort(Comparator.comparing(task -> !Boolean.TRUE.equals(task.getPinned())));
            resource.setPinnedTaskCount((int) resource.getTaskList().stream()
                    .filter(task -> Boolean.TRUE.equals(task.getPinned())).count());
        }
    }
}
