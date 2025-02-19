package cn.bctools.aps.vo.schedule.report;

import cn.bctools.aps.entity.enums.PlanTaskStatusEnum;
import cn.bctools.aps.vo.schedule.PlanScheduleReportVO;
import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * @author jvs
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@ApiModel("排产结果可视化——工作任务表")
public class PlanTaskReportVO extends PlanScheduleReportVO {

    @ApiModelProperty(value = "任务id")
    private String id;

    @ApiModelProperty("任务编码")
    private String code;

    @ApiModelProperty("订单号")
    private String orderCode;

    @ApiModelProperty("工序编码")
    private String processCode;

    @ApiModelProperty("工序名")
    private String processName;

    @ApiModelProperty("订单产物名")
    private String materialName;

    @ApiModelProperty("计划制造数量")
    private BigDecimal scheduledQuantity;

    @ApiModelProperty("计划主资源名")
    private String mainResourceName;

    @ApiModelProperty("计划开始时间")
    @JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime startTime;

    @ApiModelProperty("计划完成时间")
    @JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime endTime;

    /**
     * 预览时，不显示状态,因为没有存储状态
     */
    @ApiModelProperty("任务状态")
    private PlanTaskStatusEnum taskStatus;

    /**
     * 预览时，不计算逾期时长，因为没有存储报工时间
     */
    @ApiModelProperty(value = "逾期时长", notes = "任务是否未按时完成。格式化后的字符串")
    private String overdueTimeString;
}
