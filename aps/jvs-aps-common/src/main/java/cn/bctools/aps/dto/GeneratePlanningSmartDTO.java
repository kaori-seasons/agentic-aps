package cn.bctools.aps.dto;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.experimental.Accessors;

import javax.validation.constraints.NotBlank;

/**
 * @author jvs
 */
@Data
@Accessors(chain = true)
@ApiModel("生成排产计划")
public class GeneratePlanningSmartDTO {
    @ApiModelProperty(value = "排产策略id", required = true)
    @NotBlank(message = "未选择排产策略")
    private String planningStrategyId;
}
