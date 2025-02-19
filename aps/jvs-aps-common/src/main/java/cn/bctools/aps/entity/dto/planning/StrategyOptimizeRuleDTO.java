package cn.bctools.aps.entity.dto.planning;

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
@ApiModel("排产策略——优化规则")
public class StrategyOptimizeRuleDTO {
    @ApiModelProperty(value = "约束key", required = true)
    @NotBlank(message = "约束不能为空")
    private String constraintKey;

    @ApiModelProperty(value = "约束名", required = true)
    @NotBlank(message = "约束名不能为空")
    private String constraintName;
}
