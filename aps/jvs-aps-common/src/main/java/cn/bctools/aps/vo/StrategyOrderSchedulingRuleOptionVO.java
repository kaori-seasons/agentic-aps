package cn.bctools.aps.vo;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

/**
 * @author jvs
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@ApiModel("订单规则可选项")
public class StrategyOrderSchedulingRuleOptionVO {
    @ApiModelProperty(value = "字段key")
    private String fieldKey;

    @ApiModelProperty(value = "字段名")
    private String fieldName;
}
