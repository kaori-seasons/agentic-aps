package cn.windwheel.aps.entity.enums;

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
    FFD_LATE_ACCEPTANCE("降序首次适应 + 晚期接受"),

    TF_TABU("Timefold 禁忌搜索"),
    TF_LATE_ACCEPTANCE("Timefold 晚期接受"),
    TF_SIMULATED_ANNEALING("Timefold 模拟退火"),
    TF_GREAT_DELUGE("Timefold 大洪水"),
    TF_TABU_OSCILLATION("Timefold 禁忌搜索 + 战略振荡");

    private final String desc;

    public boolean isTimefold() {
        return switch (this) {
            case TF_TABU, TF_LATE_ACCEPTANCE, TF_SIMULATED_ANNEALING, TF_GREAT_DELUGE, TF_TABU_OSCILLATION -> true;
            default -> false;
        };
    }
}