package cn.bctools.aps.vo;

import cn.bctools.aps.entity.dto.planning.StrategyConfigDTO;
import cn.bctools.aps.entity.dto.planning.StrategyOptimizeRuleDTO;
import cn.bctools.aps.entity.dto.planning.StrategyOrderSchedulingRuleDTO;
import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;
import java.util.List;

/**
 * @author jvs
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@ApiModel("策略详情")
public class DetailPlanningStrategyVO extends BaseVO {
    @ApiModelProperty(value = "名称")
    private String name;

    @ApiModelProperty(value = "开始时间")
    @JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime beginTime;

    @ApiModelProperty(value = "false-无效，true-有效")
    private Boolean active;

    @ApiModelProperty(value = "策略基本配置")
    private StrategyConfigDTO config;

    @ApiModelProperty(value = "排产规则")
    private List<StrategyOrderSchedulingRuleDTO> orderSchedulingRules;

    @ApiModelProperty(value = "优化规则")
    private List<StrategyOptimizeRuleDTO> optimizeRules;
}
