package cn.bctools.aps.vo.schedule.report;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * @author jvs
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@ApiModel("甘特图中的任务详情")
public class PlanTaskGanttDetailVO {

    @ApiModelProperty("任务编码")
    private String code;

    @ApiModelProperty("订单号")
    private String orderCode;

    @ApiModelProperty("订单产物编码")
    private String materialCode;

    @ApiModelProperty("订单产物名")
    private String materialName;

    @ApiModelProperty("订单产物需求数量")
    private BigDecimal orderMaterialQuantity;

    @ApiModelProperty("计划制造数量")
    private BigDecimal scheduledQuantity;

    @ApiModelProperty("工序编码")
    private String processCode;

    @ApiModelProperty("工序名")
    private String processName;

    @ApiModelProperty("计划主资源编码")
    private String mainResourceCode;

    @ApiModelProperty("计划主资源名")
    private String mainResourceName;

    @ApiModelProperty("计划开始时间")
    @JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime startTime;

    @ApiModelProperty("计划完成时间")
    @JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime endTime;

    @ApiModelProperty("计划总工时")
    private String totalTimeString;

    @ApiModelProperty("完成时间")
    @JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime completionTime;

    @ApiModelProperty("已完成数量")
    private BigDecimal quantityCompleted;

    @ApiModelProperty("所需物料")
    private List<PlanTaskInputMaterialVO> inputMaterials;

    @ApiModelProperty("是否是合并任务")
    private Boolean mergeTask;

    @ApiModelProperty("合并任务的子任务")
    private List<PlanTaskGanttDetailVO> childTasks;

    @ApiModelProperty("true-已调整， false-未调整")
    private Boolean adjusted;

}
