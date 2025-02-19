package cn.bctools.aps.solve.model;

import cn.bctools.aps.annotation.Comment;
import cn.bctools.aps.entity.dto.planning.StrategyConfigDTO;
import cn.bctools.aps.entity.dto.planning.StrategyOptimizeRuleDTO;
import cn.bctools.aps.entity.dto.planning.StrategyOrderSchedulingRuleDTO;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * @author jvs
 * 排产策略
 */
@Data
public class PlanningStrategy {
    @Comment("开始时间")
    private LocalDateTime beginTime;

    @Comment("策略基本配置")
    private StrategyConfigDTO config;

    @Comment("排产规则")
    private List<StrategyOrderSchedulingRuleDTO> orderSchedulingRules;

    @Comment("优化规则")
    private List<StrategyOptimizeRuleDTO> optimizeRules;
}
