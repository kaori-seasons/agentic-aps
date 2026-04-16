package cn.windwheel.aps.solve.score;

import cn.windwheel.aps.entity.dto.planning.ConstraintWeightConfigDTO;
import cn.windwheel.common.utils.ObjectNull;

/**
 * 动态约束权重上下文，提供获取配置的方法
 */
public class DynamicConstraintWeightContext {
    private static final ThreadLocal<ConstraintWeightConfigDTO> WEIGHT_CONFIG_THREAD_LOCAL = new ThreadLocal<>();

    public static void setWeightConfig(ConstraintWeightConfigDTO config) {
        WEIGHT_CONFIG_THREAD_LOCAL.set(config);
    }

    public static ConstraintWeightConfigDTO getWeightConfig() {
        ConstraintWeightConfigDTO configDTO = WEIGHT_CONFIG_THREAD_LOCAL.get();
        if (ObjectNull.isNull(configDTO)) {
            return new ConstraintWeightConfigDTO();
        }
        return configDTO;
    }

    public static void clear() {
        WEIGHT_CONFIG_THREAD_LOCAL.remove();
    }
}