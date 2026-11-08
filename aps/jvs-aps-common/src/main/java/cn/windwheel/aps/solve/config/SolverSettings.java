package cn.windwheel.aps.solve.config;

import ai.timefold.solver.core.api.score.buildin.hardsoftlong.HardSoftLongScore;
import cn.windwheel.aps.entity.dto.planning.ConstraintWeightConfigDTO;
import cn.windwheel.aps.entity.dto.planning.StrategyConfigDTO;
import cn.windwheel.aps.entity.enums.SolverProfileEnum;
import cn.windwheel.common.exception.BusinessException;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

/** Defaults and validation are shared by persistence and both engine builders. */
public final class SolverSettings {
    private SolverSettings() { }

    public static SolverProfileEnum profile(StrategyConfigDTO config) {
        return config.getSolverProfile() == null ? SolverProfileEnum.DEFAULT : config.getSolverProfile();
    }

    public static int acceptedCount(StrategyConfigDTO config) {
        if (config.getAcceptedCountLimit() != null) {
            return config.getAcceptedCountLimit();
        }
        return switch (profile(config)) {
            case TF_LATE_ACCEPTANCE, TF_SIMULATED_ANNEALING, TF_GREAT_DELUGE -> 1;
            default -> 1000;
        };
    }

    public static long maxSeconds(StrategyConfigDTO config) {
        return config.getMaxSolveTimeSeconds() == null ? 3600L : config.getMaxSolveTimeSeconds();
    }

    public static int tabuSize(StrategyConfigDTO config) {
        return config.getEntityTabuSize() == null ? 7 : config.getEntityTabuSize();
    }

    public static int lateAcceptanceSize(StrategyConfigDTO config) {
        return config.getLateAcceptanceSize() == null ? 400 : config.getLateAcceptanceSize();
    }

    public static String temperature(StrategyConfigDTO config) {
        return config.getSimulatedAnnealingStartingTemperature() == null
                ? "0hard/1000soft" : config.getSimulatedAnnealingStartingTemperature();
    }

    public static BigDecimal delugeIncrement(StrategyConfigDTO config) {
        return config.getGreatDelugeWaterLevelIncrementRatio() == null
                ? new BigDecimal("0.000000005") : config.getGreatDelugeWaterLevelIncrementRatio();
    }

    public static void validate(StrategyConfigDTO config) {
        require(config != null, "排产策略配置不能为空");
        require(config.getDirection() != null, "排产方向不能为空");
        require(maxSeconds(config) > 0 && maxSeconds(config) <= Integer.MAX_VALUE,
                "求解总时长必须大于0且不超过2147483647秒");
        require(config.getMaxNoImprovementTime() == null
                        || (config.getMaxNoImprovementTime() >= 0 && config.getMaxNoImprovementTime() <= Integer.MAX_VALUE),
                "无改进时长必须在0到2147483647秒之间");
        require(acceptedCount(config) > 0, "每步采纳候选数量必须大于0");
        switch (profile(config)) {
            case FFD_TABU, TF_TABU, TF_TABU_OSCILLATION -> require(tabuSize(config) > 0, "禁忌长度必须大于0");
            case FFD_LATE_ACCEPTANCE, TF_LATE_ACCEPTANCE -> require(lateAcceptanceSize(config) > 0, "晚期接受窗口必须大于0");
            case TF_SIMULATED_ANNEALING -> {
                HardSoftLongScore score;
                try {
                    score = HardSoftLongScore.parseScore(temperature(config));
                } catch (IllegalArgumentException e) {
                    throw new BusinessException("模拟退火初始温度格式错误，示例：0hard/1000soft");
                }
                require(score.initScore() == 0 && score.hardScore() >= 0 && score.softScore() >= 0
                        && (score.hardScore() > 0 || score.softScore() > 0), "模拟退火初始温度各级必须非负且至少一级大于0");
            }
            case TF_GREAT_DELUGE -> require(delugeIncrement(config).signum() > 0
                    && delugeIncrement(config).compareTo(BigDecimal.ONE) < 0, "大洪水水位增量比例必须大于0且小于1");
            default -> { }
        }
        weightProperties(config); // Validate weights even before persisting the strategy.
    }

    public static Map<String, String> weightProperties(StrategyConfigDTO config) {
        ConstraintWeightConfigDTO weights = config.getConstraintWeightConfig() == null
                ? new ConstraintWeightConfigDTO() : config.getConstraintWeightConfig();
        Map<String, String> properties = new LinkedHashMap<>();
        addWeight(properties, "priorityWeight", weights.getPriorityWeight(), 50);
        addWeight(properties, "finishEarlyWeight", weights.getFinishEarlyWeight(), 1);
        addWeight(properties, "minimizeTardinessWeight", weights.getMinimizeTardinessWeight(), 1);
        addWeight(properties, "balanceResourceLoadWeight", weights.getBalanceResourceLoadWeight(), 1);
        addWeight(properties, "minimizeGapBetweenDependentTasksWeight", weights.getMinimizeGapBetweenDependentTasksWeight(), 1);
        addWeight(properties, "minimizeSetupTimeWeight", weights.getMinimizeSetupTimeWeight(), 0);
        return properties;
    }

    private static void addWeight(Map<String, String> properties, String name, Integer value, int fallback) {
        require(value == null || value >= 0, "约束权重不能为负数：" + name);
        properties.put(name, String.valueOf(value == null ? fallback : value));
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new BusinessException(message);
        }
    }
}
