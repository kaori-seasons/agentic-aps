package cn.windwheel.aps.solve.timefold;

import cn.windwheel.aps.entity.dto.planning.StrategyConfigDTO;
import cn.windwheel.aps.solve.model.MainProductionResource;
import cn.windwheel.aps.solve.model.PlanningStrategy;
import cn.windwheel.aps.solve.model.ProductionTask;
import cn.windwheel.aps.solve.model.SchedulingSolution;
import cn.windwheel.aps.solve.timefold.TimefoldConstraintProvider;
import cn.windwheel.common.utils.ObjectNull;
import cn.windwheel.common.utils.TenantContextHolder;
import ai.timefold.solver.core.api.score.stream.ConstraintStreamImplType;
import ai.timefold.solver.core.api.solver.SolverFactory;
import ai.timefold.solver.core.api.solver.SolverManager;
import ai.timefold.solver.core.config.constructionheuristic.ConstructionHeuristicPhaseConfig;
import ai.timefold.solver.core.config.solver.SolverConfig;
import ai.timefold.solver.core.config.solver.termination.TerminationCompositionStyle;
import ai.timefold.solver.core.config.solver.termination.TerminationConfig;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;

/**
 * @author jvs
 * 求解配置
 */
@Component
public class TimefoldManageConfigurator {

    /**
     * 求解器
     * Map<租户id, 求解器>
     */
    private static final Map<String, SolverManager<SchedulingSolution, UUID>> SOLVER_MANAGER_MAP = new java.util.concurrent.ConcurrentHashMap<>();

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
                .withConstraintProviderClass(TimefoldConstraintProvider.class)
                .withConstraintStreamImplType(ConstraintStreamImplType.BAVET)
                .withTerminationConfig(terminationConfig)
                .withMoveThreadCount("NONE");

        // 固定随机种子以支持复现
        if (config.getRandomSeed() != null) {
            solverConfig.withRandomSeed(config.getRandomSeed());
        }

        // 根据策略配置设置算法 Phase
        if (cn.windwheel.aps.entity.enums.SolverProfileEnum.FFD_TABU.equals(config.getSolverProfile())) {
            ConstructionHeuristicPhaseConfig chPhaseConfig = buildListVariableCompatibleConstructionHeuristicPhase();
            ai.timefold.solver.core.config.localsearch.LocalSearchPhaseConfig lsPhaseConfig = new ai.timefold.solver.core.config.localsearch.LocalSearchPhaseConfig()
                    .withAcceptorConfig(new ai.timefold.solver.core.config.localsearch.decider.acceptor.LocalSearchAcceptorConfig().withEntityTabuSize(config.getEntityTabuSize() != null ? config.getEntityTabuSize() : 7))
                    .withForagerConfig(new ai.timefold.solver.core.config.localsearch.decider.forager.LocalSearchForagerConfig().withAcceptedCountLimit(config.getAcceptedCountLimit() != null ? config.getAcceptedCountLimit() : 1000));

            solverConfig.withPhases(chPhaseConfig, lsPhaseConfig);
        } else if (cn.windwheel.aps.entity.enums.SolverProfileEnum.FFD_LATE_ACCEPTANCE.equals(config.getSolverProfile())) {
            ConstructionHeuristicPhaseConfig chPhaseConfig = buildListVariableCompatibleConstructionHeuristicPhase();
            ai.timefold.solver.core.config.localsearch.LocalSearchPhaseConfig lsPhaseConfig = new ai.timefold.solver.core.config.localsearch.LocalSearchPhaseConfig()
                    .withAcceptorConfig(new ai.timefold.solver.core.config.localsearch.decider.acceptor.LocalSearchAcceptorConfig().withLateAcceptanceSize(config.getLateAcceptanceSize() != null ? config.getLateAcceptanceSize() : 400))
                    .withForagerConfig(new ai.timefold.solver.core.config.localsearch.decider.forager.LocalSearchForagerConfig().withAcceptedCountLimit(config.getAcceptedCountLimit() != null ? config.getAcceptedCountLimit() : 1000));

            solverConfig.withPhases(chPhaseConfig, lsPhaseConfig);
        }

        // 创建SolverManager实例
        SolverFactory<SchedulingSolution> solverFactory = SolverFactory.create(solverConfig);
        SolverManager<SchedulingSolution, UUID> solverManager = SolverManager.create(solverFactory);
        SOLVER_MANAGER_MAP.put(TenantContextHolder.getTenantId(), solverManager);
        return solverManager;
    }

    /**
     * 当前解模型使用 PlanningListVariable。
     * 对这类模型，OptaPlanner 9 不支持在 Construction Heuristic phase 上显式指定
     * FIRST_FIT_DECREASING 等 constructionHeuristicType，因此仅保留 phase，
     * 由求解器选择兼容的 list-variable 构造方式。
     */
    private ConstructionHeuristicPhaseConfig buildListVariableCompatibleConstructionHeuristicPhase() {
        return new ConstructionHeuristicPhaseConfig();
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
        cn.windwheel.aps.solve.config.SolveManageConfigurator.validate(config);
        if (config.getScoreCalculationCountLimit() != null) {
            terminationConfig.setSpentLimit(SOLVE_TIME_LIMIT_DEFAULT);
            terminationConfig.setScoreCalculationCountLimit(config.getScoreCalculationCountLimit());
            return terminationConfig;
        }
        terminationConfig.setSpentLimit(Duration.ofSeconds(config.getSolveTimeSeconds() == null ? 120 : config.getSolveTimeSeconds()));
        Long maxNoImprovementTime = config.getMaxNoImprovementTime();
        if (maxNoImprovementTime != null && maxNoImprovementTime > 0) {
            terminationConfig.setUnimprovedSecondsSpentLimit(maxNoImprovementTime);
        }
        return terminationConfig;
    }


    /**
     * 移除SolverManager实例
     */
    public void release() {
        removeSolverManager();
    }

    @javax.annotation.PreDestroy
    public void shutdown() {
        SOLVER_MANAGER_MAP.values().forEach(SolverManager::close);
        SOLVER_MANAGER_MAP.clear();
    }

    private void removeSolverManager() {
        SolverManager<SchedulingSolution, UUID> solverManager = SOLVER_MANAGER_MAP.remove(TenantContextHolder.getTenantId());
        if (solverManager != null) {
            solverManager.close();
        }
    }
}
