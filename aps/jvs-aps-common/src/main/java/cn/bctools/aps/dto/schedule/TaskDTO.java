package cn.bctools.aps.dto.schedule;

import cn.bctools.aps.entity.dto.ProcessRouteNodePropertiesDTO;
import cn.bctools.aps.entity.dto.plan.PlanTaskInputMaterialDTO;
import cn.bctools.aps.entity.enums.PlanTaskStatusEnum;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

/**
 * @author jvs
 */
@Data
@Accessors(chain = true)
@ApiModel("任务属性")
public class TaskDTO {
    @ApiModelProperty("任务id")
    private String id;

    @ApiModelProperty("任务编码")
    private String code;

    @ApiModelProperty("当前任务作为合并任务的子任务时，存储合并任务的编码")
    private String mergeTaskCode;

    @ApiModelProperty("源任务编码（拆分后的子任务记录来源任务编码）")
    private String originTaskCode;

    @ApiModelProperty("主生产订单id")
    private String mainOrderId;

    @ApiModelProperty("所属生产订单id")
    private String productionOrderId;

    @ApiModelProperty("主产物计划生产数量")
    private BigDecimal scheduledQuantity;

    @ApiModelProperty(value = "生产工序")
    private ProcessRouteNodePropertiesDTO processInfo;

    @ApiModelProperty(value = "前置任务编码集合")
    private Set<String> frontTaskCodes;

    @ApiModelProperty(value = "后置任务编码集合")
    private Set<String> nextTaskCodes;

    @ApiModelProperty("主资源id")
    private String mainResourceId;

    @ApiModelProperty("计划开始时间")
    private LocalDateTime startTime;

    @ApiModelProperty("计划完成时间")
    private LocalDateTime endTime;

    @ApiModelProperty("是否是工序任务链中的首道工序任务")
    private Boolean startTask;

    @ApiModelProperty("是否是任务链中的最后一个任务")
    private Boolean endTask;

    @ApiModelProperty("是否是补充生产任务")
    private Boolean supplement;

    @ApiModelProperty("是否锁定任务")
    private Boolean pinned;

    @ApiModelProperty("是否是合并任务")
    private Boolean mergeTask;

    @ApiModelProperty("显示颜色")
    private String color;

    @ApiModelProperty(value = "输入物料")
    private List<PlanTaskInputMaterialDTO> inputMaterials;

    @ApiModelProperty("任务状态")
    private PlanTaskStatusEnum taskStatus;

    @ApiModelProperty("是否丢弃（被丢弃的任务不再显示到排程结果中）")
    private Boolean discard;

    @ApiModelProperty("true-合规，false-不合规")
    private Boolean compliant;

    @ApiModelProperty("最近一次报工完成时间")
    private LocalDateTime lastCompletionTime;

    @ApiModelProperty("完成数量")
    private BigDecimal quantityCompleted;

    @ApiModelProperty(value = "true-已调整， false-未调整", notes = "用以区分是不是已调整但未重排的任务")
    private Boolean adjusted;
}
