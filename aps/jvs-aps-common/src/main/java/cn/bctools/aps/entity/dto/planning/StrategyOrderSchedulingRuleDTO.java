package cn.bctools.aps.entity.dto.planning;

import cn.bctools.aps.entity.enums.SortRuleEnum;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.experimental.Accessors;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

/**
 * @author jvs
 */
@Data
@Accessors(chain = true)
@ApiModel("排产策略——订单排产规则")
public class StrategyOrderSchedulingRuleDTO {
    @ApiModelProperty(value = "字段key", required = true)
    @NotBlank(message = "属性名不能为空")
    private String fieldKey;

    @ApiModelProperty(value = "字段名")
    private String fieldName;

    @ApiModelProperty(value = "排序规则", required = true)
    @NotNull(message = "排序规则不能为空")
    private SortRuleEnum sortRule;
}
