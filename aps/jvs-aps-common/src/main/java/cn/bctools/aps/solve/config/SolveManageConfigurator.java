package cn.bctools.aps.solve.config;

import cn.bctools.aps.entity.dto.planning.StrategyConfigDTO;
import cn.bctools.aps.solve.model.MainProductionResource;
import cn.bctools.aps.solve.model.PlanningStrategy;
import cn.bctools.aps.solve.model.ProductionTask;
import cn.bctools.aps.solve.model.SchedulingSolution;
import cn.bctools.aps.solve.score.ApsHardSoftScoreConstraintProvider;
import cn.bctools.common.utils.ObjectNull;
import cn.bctools.common.utils.TenantContextHolder;
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
