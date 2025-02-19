package cn.bctools.aps.entity.dto.planning;

import cn.bctools.aps.entity.enums.PlanningDirectionEnum;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.experimental.Accessors;

import javax.validation.constraints.NotNull;

/**
 * @author jvs
 */
@Data
@Accessors(chain = true)
@ApiModel("排产策略——基本配置")
public class StrategyConfigDTO {
    @ApiModelProperty(value = "排产方向", required = true)
    @NotNull(message = "排产方向不能为空")
    private PlanningDirectionEnum direction;

    @ApiModelProperty(value = "true-约束物料, false-不约束物料", notes = "当为false时,不校验物料齐套")
    private Boolean materialConstrained;

    @ApiModelProperty(value = "无改进时长（单位：秒）", notes = "在指定时间内未找到更优解时终止计算")
    private Long maxNoImprovementTime;
}
