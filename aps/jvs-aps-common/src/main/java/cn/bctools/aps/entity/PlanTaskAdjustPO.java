package cn.bctools.aps.entity;

import cn.bctools.aps.entity.dto.ProcessRouteNodePropertiesDTO;
import cn.bctools.aps.entity.dto.plan.PlanTaskInputMaterialDTO;
import cn.bctools.aps.entity.enums.PlanTaskStatusEnum;
import cn.bctools.aps.entity.handler.PlanTaskInputMaterialTypeHandler;
import cn.bctools.database.entity.po.BasalPo;
import cn.bctools.database.handler.Fastjson2TypeHandler;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

/**
 * @author jvs
 * 待处理的任务调整信息表
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Accessors(chain = true)
@TableName(value = "jvs_plan_task_adjust", autoResultMap = true)
public class PlanTaskAdjustPO extends BasalPo implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.ASSIGN_UUID)
    private String id;

    /**
     * 任务编码
     */
    @TableField("`code`")
    private String code;

    /**
     * 当前任务作为合并任务的子任务时，存储合并任务的编码
     */
    @TableField("merge_task_code")
    private String mergeTaskCode;

    /**
     * 源任务编码（拆分后的子任务记录来源任务编码）
     */
    @TableField("origin_task_code")
    private String originTaskCode;

    /**
     * 主生产订单id
     * 任务可能是主生产订单的补充生产订单，添加此属性，可以更快根据主生产订单找到子生产订单
     */
    @TableField("main_order_id")
    private String mainOrderId;

    /**
     * 所属生产订单id
     */
    @TableField("production_order_id")
    private String productionOrderId;

    /**
     * 主产物计划生产数量
     */
    @TableField("scheduled_quantity")
    private BigDecimal scheduledQuantity;

    /**
     * 生产工序
     */
    @TableField(value = "process_info", typeHandler = Fastjson2TypeHandler.class)
    private ProcessRouteNodePropertiesDTO processInfo;

    /**
     * 前置任务编码集合
     */
    @TableField(value = "front_task_codes", typeHandler = Fastjson2TypeHandler.class)
    private Set<String> frontTaskCodes;

    /**
     * 后置任务编码集合
     */
    @TableField(value = "next_task_codes", typeHandler = Fastjson2TypeHandler.class)
    private Set<String> nextTaskCodes;

    /**
     * 主资源id
     */
    @TableField("main_resource_id")
    private String mainResourceId;

    /**
     * 计划开始时间
     */
    @TableField("start_time")
    private LocalDateTime startTime;

    /**
     * 计划完成时间
     */
    @TableField("end_time")
    private LocalDateTime endTime;

    /**
     * 是否是工序任务链中的首道工序任务
     */
    @TableField("start_task")
    private Boolean startTask;

    /**
     * 是否是任务链中的最后一个任务
     */
    @TableField("end_task")
    private Boolean endTask;

    /**
     * 是否是补充生产任务
     */
    @TableField("supplement")
    private Boolean supplement;

    /**
     * 是否锁定任务
     */
    @TableField("pinned")
    private Boolean pinned;

    /**
     * 是否是合并任务
     */
    @TableField("merge_task")
    private Boolean mergeTask;

    /**
     * 显示颜色
     */
    @TableField("color")
    private String color;

    /**
     * 输入物料
     */
    @TableField(value = "input_materials", typeHandler = PlanTaskInputMaterialTypeHandler.class)
    private List<PlanTaskInputMaterialDTO> inputMaterials;

    /**
     * 任务状态
     */
    @TableField("task_status")
    private PlanTaskStatusEnum taskStatus;

    /**
     * 是否丢弃（被丢弃的任务不再显示到排程结果中）
     */
    @TableField("discard")
    private Boolean discard;

    /**
     * true-合规，false-不合规
     */
    @TableField("compliant")
    private Boolean compliant;

    /**
     * 最近一次报工完成时间
     */
    @TableField("last_completion_time")
    private LocalDateTime lastCompletionTime;

    /**
     * 完成数量
     */
    @TableField("quantity_completed")
    private BigDecimal quantityCompleted;

    /**
     * 租户id
     */
    @TableField("tenant_id")
    private String tenantId;
}
