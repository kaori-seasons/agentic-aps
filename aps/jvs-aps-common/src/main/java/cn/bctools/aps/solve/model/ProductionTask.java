package cn.bctools.aps.solve.model;

import cn.bctools.aps.annotation.Comment;
import cn.bctools.aps.entity.dto.ProcessRouteNodePropertiesDTO;
import cn.bctools.aps.entity.dto.plan.PlanTaskInputMaterialDTO;
import cn.bctools.aps.solve.calculate.ApsStartTimeListener;
import cn.bctools.aps.solve.util.TaskCalendarUtils;
import cn.bctools.aps.solve.util.TaskDurationUtils;
import cn.bctools.common.utils.ObjectNull;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import lombok.extern.slf4j.Slf4j;
import org.optaplanner.core.api.domain.entity.PlanningEntity;
import org.optaplanner.core.api.domain.entity.PlanningPin;
import org.optaplanner.core.api.domain.lookup.PlanningId;
import org.optaplanner.core.api.domain.variable.InverseRelationShadowVariable;
import org.optaplanner.core.api.domain.variable.ShadowVariable;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * @author jvs
 * 生产任务
 */
@Slf4j
@Data
@Accessors(chain = true)
@NoArgsConstructor
@PlanningEntity
public class ProductionTask {

    @Comment(value = "id", notes = "排程计算时使用，不入库。排程计算过程中id与任务编码同等作用（都能唯一标识一个任务），但业务逻辑使用时，应使用任务编码")
    @PlanningId
    private String id;

    @Comment(value = "任务编码", notes = "租户级唯一")
    private String code;

    @Comment(value = "任务优先级")
    private Integer priority;

    @Comment(value = "是否是合并任务")
    private Boolean mergeTask;

    @Comment(value = "合并任务编码", notes = "当前任务作为合并任务的子任务时，存储合并任务的编码")
    private String mergeTaskCode;

    @Comment(value = "源任务编码", notes = "当前任务作为拆分后的子任务时。 存储来源任务编码")
    private String originTaskCode;

    @Comment(value = "主生产订单id", notes = "任务可能是主生产订单的补充生产订单，添加此属性，可以更快根据主生产订单找到子生产订单")
    private String mainOrderId;

    @Comment("生产订单")
    private ProductionOrder order;

    @Comment(value = "生产物料", notes = "主产物")
    private Material material;

    @Comment(value = "生产数量", notes = "主产物生产数量")
    private BigDecimal quantity;

    @Comment("生产工序")
    private ProcessRouteNodePropertiesDTO process;

    @Comment("前置任务")
    private Set<ProductionTask> frontTasks;

    @Comment("前置任务编码集合")
    private Set<String> frontTaskCodes;

    @Comment("后置任务编码集合")
    private Set<String> nextTaskCodes;

    @Comment("欠料物料延迟记录")
    private Map<String, Duration> materialDelayMap;

    @Comment("分派主资源")
    @InverseRelationShadowVariable(sourceVariableName = "taskList")
    private MainProductionResource resource;

    @Comment("true-任务已锁定,false-任务未锁定")
    @PlanningPin
    private Boolean pinned;

    @Comment("计划开始时间")
    @ShadowVariable(variableListenerClass = ApsStartTimeListener.class, sourceEntityClass = MainProductionResource.class, sourceVariableName = "taskList")
    private LocalDateTime startTime;

    @Comment("计划完成时间")
    private LocalDateTime endTime;

    @Comment(value = "是工序任务链中的首道工序任务", notes = "可以有多个首道工序任务")
    private Boolean startTask;

    @Comment("是工序任务链中的最后一道工序任务")
    private Boolean endTask;

    @Comment(value = "是补充生产任务", notes = "生产订单商品的补充生产订单")
    private Boolean supplement;

    @Comment("输入物料")
    private List<PlanTaskInputMaterialDTO> inputMaterials;

    /**
     * 添加前置任务
     *
     * @param task 前置任务
     */
    public void addFrontTask(ProductionTask task) {
        if (ObjectNull.isNull(task)) {
            return;
        }
        if (ObjectNull.isNull(frontTaskCodes)) {
            frontTaskCodes = new HashSet<>();
            frontTasks = new HashSet<>();
        }
        frontTaskCodes.add(task.getCode());
        frontTasks.add(task);
    }

    /**
     * 移除前置任务
     *
     * @param taskCode 前置任务编码
     */
    public void removeFrontTask(String taskCode) {
        if (ObjectNull.isNull(taskCode)) {
            return;
        }
        if (ObjectNull.isNotNull(frontTasks)) {
            frontTasks.removeIf(task -> taskCode.equals(task.getCode()));
        }
        if (ObjectNull.isNotNull(frontTaskCodes)) {
            frontTaskCodes.remove(taskCode);
        }
    }


    /**
     * 添加后置任务
     *
     * @param task 后置任务
     */
    public void addNextTask(ProductionTask task) {
        if (ObjectNull.isNull(task)) {
            return;
        }
        if (ObjectNull.isNull(nextTaskCodes)) {
            nextTaskCodes = new HashSet<>();
        }
        nextTaskCodes.add(task.getCode());
    }

    /**
     * 移除后置任务
     *
     * @param taskCode 后置任务编码
     */
    public void removeNextTask(String taskCode) {
        if (ObjectNull.isNull(taskCode)) {
            return;
        }
        if (ObjectNull.isNotNull(nextTaskCodes)) {
            nextTaskCodes.remove(taskCode);
        }
    }

    /**
     * 结束时间
     *
     * @return 开始时间 + 耗时
     */
    public LocalDateTime getEndTime() {
        if (ObjectNull.isNull(startTime) || ObjectNull.isNull(resource)) {
            return null;
        }
        Duration taskDuration = calculateTaskDuration();
        if (taskDuration == null) {
            return null;
        }
        return TaskCalendarUtils.calculateEndTime(startTime, taskDuration, resource.getWorkCalendars());
    }

    /**
     * 任务持续时长（不包含休息时间，只是完成任务所需时长）
     *
     * @return 任务持续时长
     */
    public Duration calculateTaskDuration() {
        return TaskDurationUtils.calculateTaskDuration(resource.getId(), process, quantity);
    }

    @Override
    public String toString() {
        return "ProductionTask{" +
                "code='" + code + '\'' +
                '}';
    }
}
