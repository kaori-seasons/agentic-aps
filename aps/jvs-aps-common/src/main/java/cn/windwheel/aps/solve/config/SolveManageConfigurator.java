package cn.windwheel.aps.solve.config;

import cn.windwheel.aps.entity.dto.planning.StrategyConfigDTO;
import cn.windwheel.aps.solve.model.MainProductionResource;
import cn.windwheel.aps.solve.model.PlanningStrategy;
import cn.windwheel.aps.solve.model.ProductionTask;
import cn.windwheel.aps.solve.model.SchedulingSolution;
import cn.windwheel.aps.solve.score.ApsHardSoftScoreConstraintProvider;
import cn.windwheel.common.utils.ObjectNull;
import cn.windwheel.common.utils.TenantContextHolder;
import org.optaplanner.core.api.score.stream.ConstraintStreamImplType;
import org.optaplanner.core.api.solver.SolverFactory;
import org.optaplanner.core.api.solver.SolverManager;
import org.optaplanner.core.config.solver.SolverConfig;
import org.optaplanner.core.config.solver.termination.TerminationCompositionStyle;
import org.optaplanner.core.config.solver.termination.TerminationConfig;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * @author jvs
 * 求解配置
 */
@Component
public class SolveManageConfigurator {

    /**
     * 求解器
     * Map<租户id, 求解器>
     */
    private static final Map<String, SolverManager<SchedulingSolution, UUID>> SOLVER_MANAGER_MAP = new HashMap<>();

    /**
     * 求解运行最大时长默认值
     */
    private static final Duration SOLVE_TIME_LIMIT_DEFAULT = Duration.ofHours(1);

    /**
     * 初始化SolverManager实例
     *
     * @param strategy 策略
     * @return SolverManager实例
     */
    public SolverManager<SchedulingSolution, UUID> initSolverManager(PlanningStrategy strategy) {
        // 移除已存在的SolverManager
        removeSolverManager();
        // 创建SolverManager
        return createSolverManager(strategy.getConfig());
    }

    /**
     * 获取SolverManager实例
     *
     * @return SolverManager实例
     */
    public SolverManager<SchedulingSolution, UUID> getSolverManager() {
        return SOLVER_MANAGER_MAP.get(TenantContextHolder.getTenantId());
    }

    /**
     * 创建SolverManager实例
     *
     * @param config 策略配置
     * @return SolverManager实例
     */
    private SolverManager<SchedulingSolution, UUID> createSolverManager(StrategyConfigDTO config) {
        // 终止配置
        TerminationConfig terminationConfig = buildTerminationConfig(config);
        // 求解配置
        SolverConfig solverConfig = new SolverConfig()
                .withSolutionClass(SchedulingSolution.class)
                .withEntityClasses(ProductionTask.class, MainProductionResource.class)
                .withConstraintProviderClass(ApsHardSoftScoreConstraintProvider.class)
                .withConstraintStreamImplType(ConstraintStreamImplType.BAVET)
                .withTerminationConfig(terminationConfig)
                .withMoveThreadCount("AUTO");

        // 固定随机种子以支持复现
        if (config.getRandomSeed() != null) {
            solverConfig.withRandomSeed(config.getRandomSeed());
        }

        // 根据策略配置设置算法 Phase
        if (cn.windwheel.aps.entity.enums.SolverProfileEnum.FFD_TABU.equals(config.getSolverProfile())) {
            org.optaplanner.core.config.constructionheuristic.ConstructionHeuristicPhaseConfig chPhaseConfig = new org.optaplanner.core.config.constructionheuristic.ConstructionHeuristicPhaseConfig()
                    .withConstructionHeuristicType(org.optaplanner.core.config.constructionheuristic.ConstructionHeuristicType.FIRST_FIT_DECREASING);

            org.optaplanner.core.config.localsearch.LocalSearchPhaseConfig lsPhaseConfig = new org.optaplanner.core.config.localsearch.LocalSearchPhaseConfig()
                    .withLocalSearchType(org.optaplanner.core.config.localsearch.LocalSearchType.TABU_SEARCH)
                    .withAcceptorConfig(new org.optaplanner.core.config.localsearch.decider.acceptor.LocalSearchAcceptorConfig().withEntityTabuSize(config.getEntityTabuSize() != null ? config.getEntityTabuSize() : 7))
                    .withForagerConfig(new org.optaplanner.core.config.localsearch.decider.forager.LocalSearchForagerConfig().withAcceptedCountLimit(config.getAcceptedCountLimit() != null ? config.getAcceptedCountLimit() : 1000));

            solverConfig.withPhases(chPhaseConfig, lsPhaseConfig);
        } else if (cn.windwheel.aps.entity.enums.SolverProfileEnum.FFD_LATE_ACCEPTANCE.equals(config.getSolverProfile())) {
            org.optaplanner.core.config.constructionheuristic.ConstructionHeuristicPhaseConfig chPhaseConfig = new org.optaplanner.core.config.constructionheuristic.ConstructionHeuristicPhaseConfig()
                    .withConstructionHeuristicType(org.optaplanner.core.config.constructionheuristic.ConstructionHeuristicType.FIRST_FIT_DECREASING);

            org.optaplanner.core.config.localsearch.LocalSearchPhaseConfig lsPhaseConfig = new org.optaplanner.core.config.localsearch.LocalSearchPhaseConfig()
                    .withAcceptorConfig(new org.optaplanner.core.config.localsearch.decider.acceptor.LocalSearchAcceptorConfig().withLateAcceptanceSize(config.getLateAcceptanceSize() != null ? config.getLateAcceptanceSize() : 400))
                    .withForagerConfig(new org.optaplanner.core.config.localsearch.decider.forager.LocalSearchForagerConfig().withAcceptedCountLimit(config.getAcceptedCountLimit() != null ? config.getAcceptedCountLimit() : 1000));

            solverConfig.withPhases(chPhaseConfig, lsPhaseConfig);
        }

        // 创建SolverManager实例
        SolverFactory<SchedulingSolution> solverFactory = SolverFactory.create(solverConfig);
        SolverManager<SchedulingSolution, UUID> solverManager = SolverManager.create(solverFactory);
        SOLVER_MANAGER_MAP.put(TenantContextHolder.getTenantId(), solverManager);
        return solverManager;
    }

    /**
     * 构建终止配置
     *
     * @param config 策略配置
     * @return 终止配置
     */
    private TerminationConfig buildTerminationConfig(StrategyConfigDTO config) {
        TerminationConfig terminationConfig = new TerminationConfig();
        terminationConfig.setTerminationCompositionStyle(TerminationCompositionStyle.OR);
        terminationConfig.setSpentLimit(SOLVE_TIME_LIMIT_DEFAULT);
        Long maxNoImprovementTime = config.getMaxNoImprovementTime();
        if (ObjectNull.isNull(maxNoImprovementTime) || maxNoImprovementTime <= 0) {
            terminationConfig.setBestScoreFeasible(true);
        } else {
            terminationConfig.setUnimprovedSecondsSpentLimit(maxNoImprovementTime);
        }
        return terminationConfig;
    }


    /**
     * 移除SolverManager实例
     */
    private void removeSolverManager() {
        SolverManager<SchedulingSolution, UUID> solverManager = getSolverManager();
        if (ObjectNull.isNull(solverManager)) {
            return;
        }
        solverManager.close();
        SOLVER_MANAGER_MAP.remove(TenantContextHolder.getTenantId());
    }
}
