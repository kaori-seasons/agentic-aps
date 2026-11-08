package cn.windwheel.aps.entity.dto.planning;

import cn.windwheel.aps.entity.enums.PlanningDirectionEnum;
import cn.windwheel.aps.entity.enums.SolverProfileEnum;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.experimental.Accessors;

import javax.validation.Valid;
import javax.validation.constraints.Min;
import javax.validation.constraints.Max;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Positive;
import java.math.BigDecimal;

@Data
@Accessors(chain = true)
@ApiModel("排产策略——基本配置")
public class StrategyConfigDTO {
    @ApiModelProperty(value = "排产方向", required = true)
    @NotNull(message = "排产方向不能为空")
    private PlanningDirectionEnum direction;

    @ApiModelProperty(value = "true-约束物料, false-不约束物料")
    private Boolean materialConstrained;

    @ApiModelProperty(value = "无改进时长（秒）", notes = "未设置或为0：旧引擎找到可行解即停止，Timefold使用30秒无改进限制")
    @Min(0)
    @Max(Integer.MAX_VALUE)
    private Long maxNoImprovementTime;

    @ApiModelProperty(value = "求解总时长上限（秒）", notes = "默认3600，与无改进限制任一满足即停止")
    @Positive
    @Max(Integer.MAX_VALUE)
    private Long maxSolveTimeSeconds = 3600L;

    @ApiModelProperty(value = "算法模板", notes = "DEFAULT/FFD_TABU/FFD_LATE_ACCEPTANCE使用OptaPlanner；TF_TABU/TF_LATE_ACCEPTANCE/TF_SIMULATED_ANNEALING/TF_GREAT_DELUGE/TF_TABU_OSCILLATION使用Timefold")
    private SolverProfileEnum solverProfile = SolverProfileEnum.DEFAULT;

    @ApiModelProperty(value = "禁忌长度", notes = "禁忌搜索模板使用，默认7")
    @Positive
    private Integer entityTabuSize = 7;

    @ApiModelProperty(value = "晚期接受窗口", notes = "晚期接受模板使用，默认400")
    @Positive
    private Integer lateAcceptanceSize = 400;

    @ApiModelProperty(value = "每步采纳候选数量上限", notes = "未设置时：禁忌及旧模板1000，其他Timefold模板1")
    @Positive
    private Integer acceptedCountLimit;

    @ApiModelProperty(value = "模拟退火初始温度", notes = "HardSoftLongScore格式，各级非负且至少一级大于0；需按业务基准调优")
    private String simulatedAnnealingStartingTemperature = "0hard/1000soft";

    @ApiModelProperty(value = "大洪水水位增量比例", notes = "大于0且小于1，默认0.000000005")
    private BigDecimal greatDelugeWaterLevelIncrementRatio = new BigDecimal("0.000000005");

    @Valid
    @ApiModelProperty(value = "约束权重配置")
    private ConstraintWeightConfigDTO constraintWeightConfig = new ConstraintWeightConfigDTO();

    @ApiModelProperty(value = "随机种子", notes = "固定种子使搜索顺序可复现；按时间终止时结果仍可能受机器负载影响")
    private Long randomSeed;
}
