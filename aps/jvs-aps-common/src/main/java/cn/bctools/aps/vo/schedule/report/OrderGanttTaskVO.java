package cn.bctools.aps.vo.schedule.report;

import cn.bctools.aps.entity.enums.PlanTaskStatusEnum;
import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * @author jvs
 */
@Data
@Accessors(chain = true)
@ApiModel("订单甘特图任务信息")
public class OrderGanttTaskVO {

    @ApiModelProperty("工序编码")
    private String processCode;

    @ApiModelProperty("订单id")
    private String productionOrderId;

    @ApiModelProperty("显示颜色")
    private String color;

    @ApiModelProperty("计划开始时间")
    @JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime startTime;

    @ApiModelProperty("计划完成时间")
    @JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime endTime;

    @ApiModelProperty("任务状态")
    private PlanTaskStatusEnum taskStatus;

    @ApiModelProperty(value = "逾期时长", notes = "不为空，表示任务已逾期")
    private String overdueTimeString;

    @ApiModelProperty(value = "订单延期时长", notes = "任务计划完成时间在订单交期时间后预计订单延期时长")
    private String delayTimeString;

    @ApiModelProperty(value = "单任务扩展数据", notes = "根据用户配置动态返回的数据，非合并任务的扩展数据")
    private Map<String, Object> extras;
}
