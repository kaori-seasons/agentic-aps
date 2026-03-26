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

    @ApiModelProperty(value = "求解器策略模板", notes = "DEFAULT:默认策略, FFD_TABU:降序首次适应+禁忌搜索")
    private cn.bctools.aps.entity.enums.SolverProfileEnum solverProfile = cn.bctools.aps.entity.enums.SolverProfileEnum.DEFAULT;

    @ApiModelProperty(value = "禁忌表长度", notes = "仅当 solverProfile=FFD_TABU 时生效，控制禁忌搜索中被移动实体的短期内禁止反向移动步数，推荐值:7")
    private Integer entityTabuSize = 7;

    @ApiModelProperty(value = "局部搜索评估步数限制", notes = "仅当 solverProfile=FFD_TABU 时生效，每一次Move选择时评估周边邻域解的数量，推荐值:1000")
    private Integer acceptedCountLimit = 1000;

    @ApiModelProperty(value = "约束权重配置")
    private ConstraintWeightConfigDTO constraintWeightConfig = new ConstraintWeightConfigDTO();

    @ApiModelProperty(value = "随机种子", notes = "用于保证排产结果的可复现性，固定种子则排产结果一致")
    private Long randomSeed;
}
