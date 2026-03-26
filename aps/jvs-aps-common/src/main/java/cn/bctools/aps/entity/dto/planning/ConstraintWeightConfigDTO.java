package cn.bctools.aps.entity.dto.planning;

import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class ConstraintWeightConfigDTO {

    @ApiModelProperty(value = "尽量按优先级安排 权重 (Soft)", notes = "默认 50")
    private Integer priorityWeight = 50;

    @ApiModelProperty(value = "尽早完成任务 权重 (Soft)", notes = "默认 1")
    private Integer finishEarlyWeight = 1;

    @ApiModelProperty(value = "最小化逾期 权重 (Soft)", notes = "默认 1")
    private Integer minimizeTardinessWeight = 1;

    @ApiModelProperty(value = "资源负载均衡 权重 (Soft)", notes = "默认 1")
    private Integer balanceResourceLoadWeight = 1;

    @ApiModelProperty(value = "优化任务调度的连续性 权重 (Soft)", notes = "默认 1")
    private Integer minimizeGapBetweenDependentTasksWeight = 1;

    @ApiModelProperty(value = "最小化换型时间权重 (Soft)", notes = "电子行业关注，默认0代表关闭")
    private Integer minimizeSetupTimeWeight = 0;
}