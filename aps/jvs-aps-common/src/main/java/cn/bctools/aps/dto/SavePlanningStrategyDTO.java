package cn.bctools.aps.dto;

import cn.bctools.aps.entity.dto.planning.StrategyConfigDTO;
import cn.bctools.aps.entity.dto.planning.StrategyOptimizeRuleDTO;
import cn.bctools.aps.entity.dto.planning.StrategyOrderSchedulingRuleDTO;
import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.experimental.Accessors;

import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.time.LocalDateTime;
import java.util.List;

/**
 * @author jvs
 */
@Data
@Accessors(chain = true)
@ApiModel("新增策略")
public class SavePlanningStrategyDTO {
    @ApiModelProperty(value = "名称", required = true)
    @NotBlank(message = "策略名称不能为空")
    private String name;

    @ApiModelProperty(value = "开始时间", required = true)
    @NotNull(message = "开始时间不能为空")
    @JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime beginTime;

    @ApiModelProperty(value = "false-无效，true-有效", required = true)
    @NotNull(message = "有效状态不能为空")
    private Boolean active;

    @ApiModelProperty(value = "策略基本配置", required = true)
    @NotNull(message = "请完善策略配置")
    @Valid
    private StrategyConfigDTO config;

    @ApiModelProperty(value = "排产规则")
    @Valid
    private List<StrategyOrderSchedulingRuleDTO> orderSchedulingRules;

    @ApiModelProperty(value = "优化规则")
    @Valid
    private List<StrategyOptimizeRuleDTO> optimizeRules;
}
