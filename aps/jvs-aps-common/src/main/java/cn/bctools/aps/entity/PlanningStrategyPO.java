package cn.bctools.aps.entity;

import cn.bctools.aps.entity.dto.planning.StrategyConfigDTO;
import cn.bctools.aps.entity.dto.planning.StrategyOptimizeRuleDTO;
import cn.bctools.aps.entity.dto.planning.StrategyOrderSchedulingRuleDTO;
import cn.bctools.aps.entity.handler.StrategyOptimizeRuleTypeHandler;
import cn.bctools.aps.entity.handler.StrategyOrderSchedulingRuleTypeHandler;
import cn.bctools.database.entity.po.BasalPo;
import cn.bctools.database.handler.Fastjson2TypeHandler;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

/**
 * @author jvs
 * 排产计划策略
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Accessors(chain = true)
@TableName(value = "jvs_planning_strategy", autoResultMap = true)
public class PlanningStrategyPO extends BasalPo implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.ASSIGN_UUID)
    private String id;

    /**
     * 名称
     */
    @TableField("name")
    private String name;

    /**
     * 开始时间
     */
    @TableField("begin_time")
    private LocalDateTime beginTime;

    /**
     * false-无效，true-有效
     */
    @TableField("active")
    private Boolean active;

    /**
     * 策略基本配置
     */
    @TableField(value = "config", typeHandler = Fastjson2TypeHandler.class)
    private StrategyConfigDTO config;

    /**
     * 排产规则
     */
    @TableField(value = "order_scheduling_rules", typeHandler = StrategyOrderSchedulingRuleTypeHandler.class)
    private List<StrategyOrderSchedulingRuleDTO> orderSchedulingRules;

    /**
     * 优化规则
     */
    @TableField(value = "optimize_rules", typeHandler = StrategyOptimizeRuleTypeHandler.class)
    private List<StrategyOptimizeRuleDTO> optimizeRules;

    /**
     * 是否删除 0未删除  1已删除
     */
    @TableField("del_flag")
    private Boolean delFlag;
}
