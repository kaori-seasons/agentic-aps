package cn.windwheel.aps.solve.util;

import cn.windwheel.aps.dto.schedule.TaskDTO;
import cn.windwheel.aps.solve.model.ProductionTask;

/** 求解图包含任务与资源的循环引用，持久化只传递任务的业务字段。 */
public final class SchedulingTaskMapper {
    private SchedulingTaskMapper() {}

    public static TaskDTO toDTO(ProductionTask task) {
        return new TaskDTO()
                .setId(task.getId())
                .setCode(task.getCode())
                .setMergeTaskCode(task.getMergeTaskCode())
                .setOriginTaskCode(task.getOriginTaskCode())
                .setMainOrderId(task.getMainOrderId())
                .setProductionOrderId(task.getOrder() == null ? null : task.getOrder().getId())
                .setScheduledQuantity(task.getQuantity())
                .setProcessInfo(task.getProcess())
                .setFrontTaskCodes(task.getFrontTaskCodes())
                .setNextTaskCodes(task.getNextTaskCodes())
                .setMainResourceId(task.getResource() == null ? null : task.getResource().getId())
                .setStartTime(task.getStartTime())
                .setEndTime(task.getEndTime())
                .setStartTask(task.getStartTask())
                .setEndTask(task.getEndTask())
                .setSupplement(task.getSupplement())
                .setPinned(task.getPinned())
                .setMergeTask(task.getMergeTask())
                .setColor(task.getOrder() == null ? null : task.getOrder().getColor())
                .setInputMaterials(task.getInputMaterials())
                .setDiscard(false);
    }
}
