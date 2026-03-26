package cn.bctools.aps.entity.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 求解器策略模板枚举
 */
@Getter
@AllArgsConstructor
public enum SolverProfileEnum {

    /**
     * 默认策略：依赖 OptaPlanner 内部默认的阶段配置
     */
    DEFAULT("默认策略"),

    /**
     * FFD (First Fit Decreasing) + Tabu Search
     */
    FFD_TABU("降序首次适应 + 禁忌搜索"),

    /**
     * FFD (First Fit Decreasing) + Late Acceptance
     */
    FFD_LATE_ACCEPTANCE("降序首次适应 + 晚期接受");

    private final String desc;
}