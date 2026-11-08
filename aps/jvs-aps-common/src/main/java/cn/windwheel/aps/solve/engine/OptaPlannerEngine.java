package cn.windwheel.aps.solve.engine;

import cn.windwheel.aps.entity.dto.planning.StrategyConfigDTO;
import cn.windwheel.aps.entity.enums.SolverProfileEnum;
import cn.windwheel.aps.solve.config.SolverSettings;
import cn.windwheel.aps.solve.model.MainProductionResource;
import cn.windwheel.aps.solve.model.ProductionTask;
import cn.windwheel.aps.solve.model.SchedulingSolution;
import cn.windwheel.aps.solve.score.ApsHardSoftScoreConstraintProvider;
import org.optaplanner.core.api.score.stream.ConstraintStreamImplType;
import org.optaplanner.core.api.solver.SolverFactory;
import org.optaplanner.core.api.solver.SolverManager;
import org.optaplanner.core.config.constructionheuristic.ConstructionHeuristicPhaseConfig;
import org.optaplanner.core.config.localsearch.LocalSearchPhaseConfig;
import org.optaplanner.core.config.localsearch.decider.acceptor.LocalSearchAcceptorConfig;
import org.optaplanner.core.config.localsearch.decider.forager.LocalSearchForagerConfig;
import org.optaplanner.core.config.solver.SolverConfig;
import org.optaplanner.core.config.solver.termination.TerminationConfig;
import org.optaplanner.core.config.solver.termination.TerminationCompositionStyle;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.ExecutionException;

public final class OptaPlannerEngine implements PlanningSolverEngine {
    @Override
    public boolean supports(SolverProfileEnum profile) {
        return !profile.isTimefold();
    }

    public SolverConfig buildSolverConfig(StrategyConfigDTO config) {
        SolverSettings.validate(config);
        if (!supports(SolverSettings.profile(config))) {
            throw new IllegalArgumentException("Timefold profile cannot use OptaPlanner");
        }
        TerminationConfig termination = new TerminationConfig()
                .withTerminationCompositionStyle(TerminationCompositionStyle.OR)
                .withSpentLimit(Duration.ofSeconds(SolverSettings.maxSeconds(config)));
        if (config.getMaxNoImprovementTime() == null || config.getMaxNoImprovementTime() == 0) {
            termination.setBestScoreFeasible(true);
        } else {
            termination.setUnimprovedSecondsSpentLimit(config.getMaxNoImprovementTime());
        }
        SolverConfig solver = new SolverConfig()
                .withSolutionClass(SchedulingSolution.class)
                .withEntityClasses(ProductionTask.class, MainProductionResource.class)
                .withConstraintProviderClass(ApsHardSoftScoreConstraintProvider.class)
                .withConstraintStreamImplType(ConstraintStreamImplType.BAVET)
                .withTerminationConfig(termination)
                .withMoveThreadCount("NONE");
        solver.getScoreDirectorFactoryConfig().setConstraintProviderCustomProperties(SolverSettings.weightProperties(config));
        if (config.getRandomSeed() != null) {
            solver.setRandomSeed(config.getRandomSeed());
        }
        SolverProfileEnum profile = SolverSettings.profile(config);
        if (profile != SolverProfileEnum.DEFAULT) {
            LocalSearchAcceptorConfig acceptor = new LocalSearchAcceptorConfig();
            if (profile == SolverProfileEnum.FFD_TABU) {
                // A list move changes task values; tabuing resource entities freezes entire machines.
                acceptor.setValueTabuSize(SolverSettings.tabuSize(config));
            } else {
                acceptor.setLateAcceptanceSize(SolverSettings.lateAcceptanceSize(config));
            }
            solver.withPhases(new ConstructionHeuristicPhaseConfig(), new LocalSearchPhaseConfig()
                    .withAcceptorConfig(acceptor)
                    .withForagerConfig(new LocalSearchForagerConfig()
                            .withAcceptedCountLimit(SolverSettings.acceptedCount(config))));
        }
        return solver;
    }

    @Override
    public ManagedSolverSession createSession(StrategyConfigDTO config) {
        SolverFactory<SchedulingSolution> factory = SolverFactory.create(buildSolverConfig(config));
        SolverManager<SchedulingSolution, UUID> manager = SolverManager.create(factory);
        return new ManagedSolverSession((id, problem) -> {
            var job = manager.solve(id, problem);
            return new ManagedSolverSession.Job() {
                public SchedulingSolution getFinalBestSolution() throws InterruptedException, ExecutionException {
                    return job.getFinalBestSolution();
                }
                public boolean isTerminatedEarly() {
                    return job.isTerminatedEarly();
                }
            };
        }, manager::terminateEarly, manager::close);
    }
}
