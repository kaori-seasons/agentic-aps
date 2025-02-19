package cn.bctools.aps.service.facade.impl.adjustment.merge;

import cn.bctools.aps.dto.schedule.AdjustTaskMergeDTO;
import cn.bctools.aps.dto.schedule.TaskDTO;
import cn.bctools.aps.entity.PlanTaskOrderPO;
import cn.bctools.aps.entity.dto.plan.PlanTaskInputMaterialDTO;
import cn.bctools.aps.entity.enums.PlanTaskStatusEnum;
import cn.bctools.aps.service.facade.AdjustTaskMergeFacadeService;
import cn.bctools.aps.service.facade.impl.adjustment.AbstractAdjustTask;
import cn.bctools.aps.solve.model.WorkCalendar;
import cn.bctools.aps.util.PlanUtils;
import cn.bctools.aps.vo.schedule.TaskAdjustMergeVO;
import cn.bctools.aps.vo.schedule.report.ResourceGanttTaskVO;
import cn.bctools.common.exception.BusinessException;
import cn.bctools.common.utils.BeanCopyUtil;
import cn.bctools.common.utils.ObjectNull;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * @author jvs
 * 合并任务模板方法
 */
public abstract class AbstractAdjustTaskMerge<T extends AdjustTaskMergeDTO> extends AbstractAdjustTask implements AdjustTaskMergeFacadeService<T> {

    @Override
    public TaskAdjustMergeVO merge(T input) {
        // 合并任务
        List<TaskDTO> mergeList = mergeTask(input);
        if (ObjectNull.isNull(mergeList)) {
            return null;
        }

        // 重新设置调整任务相关的任务关联关系
        List<TaskDTO> taskAdjustList = relationshipNextTaskCodes(mergeList);

        // 合并任务后，检查是否有任务不合规
        checkTaskCompliance(taskAdjustList);

        // 保存任务
        taskAdjustFacadeService.saveAdjustTasks(taskAdjustList);

        // 返回将合并任务结果转换为前端展示的数据结构
        return convertTaskAdjustToVo(taskAdjustList);
    }

    /**
     * 合并任务
     *
     * @param input 合并任务参数
     * @return 合并后的任务
     */
    protected abstract List<TaskDTO> mergeTask(T input);

    /**
     * 合并任务
     *
     * @param taskAdjusts 待合并任务
     * @return 合并任务以及影响的任务
     */
    protected List<TaskDTO> executeMergeTasks(List<TaskDTO> taskAdjusts) {
        if (ObjectNull.isNull(taskAdjusts) || taskAdjusts.size() < 2) {
            throw new BusinessException("待合并任务不存在或少于两个任务");
        }
        boolean hasCompletedTask = taskAdjusts.stream().anyMatch(task -> PlanTaskStatusEnum.COMPLETED.equals(task.getTaskStatus()));
        if (hasCompletedTask) {
            throw new BusinessException("不能合并已完成的任务");
        }
        boolean hasPinnedTask = taskAdjusts.stream().anyMatch(TaskDTO::getPinned);
        if (hasPinnedTask) {
            throw new BusinessException("不能合并已锁定的任务");
        }

        // 校验是否可合并
        checkCanMerge(taskAdjusts);

        // 合并任务
        return doMergeTask(taskAdjusts);
    }

    /**
     * 校验是否可以合并
     *
     * @param taskAdjusts 待合并任务
     */
    private void checkCanMerge(List<TaskDTO> taskAdjusts) {
        // 主产物相同且工序相同，可合并
        Set<String> orderIds = new HashSet<>();
        Set<String> mergeTaskCodes = new HashSet<>();
        taskAdjusts.forEach(taskAdjust -> {
            if (taskAdjust.getMergeTask()) {
                mergeTaskCodes.add(taskAdjust.getCode());
            } else {
                orderIds.add(taskAdjust.getProductionOrderId());
            }
        });
        // 查询合并任务的子任务的订单id。 Map<合并任务编码, 合并任务的子任务订单id集合>
        Map<String, List<String>> mergeTaskOrderIdMap = taskAdjustFacadeService.listTaskByMergeTaskCodes(mergeTaskCodes).stream()
                .filter(task -> !task.getDiscard())
                .collect(Collectors.groupingBy(TaskDTO::getMergeTaskCode, Collectors.mapping(TaskDTO::getProductionOrderId, Collectors.toList())));
        if (ObjectNull.isNotNull(mergeTaskOrderIdMap)) {
            List<String> mergeTaskOrderIds = mergeTaskOrderIdMap.values().stream().flatMap(Collection::stream).toList();
            orderIds.addAll(mergeTaskOrderIds);
        }
        Map<String, PlanTaskOrderPO> planTaskOrderMap = planTaskOrderService.listByOrderIds(orderIds).stream()
                .collect(Collectors.toMap(PlanTaskOrderPO::getOrderId, Function.identity()));
        boolean noSameProcess = taskAdjusts.stream()
                .map(task -> {
                    String orderId = task.getProductionOrderId();
                    if (task.getMergeTask()) {
                        orderId = mergeTaskOrderIdMap.get(task.getCode()).get(0);
                    }
                    return planTaskOrderMap.get(orderId).getOrderMaterialInfo().getCode() + "_" + task.getProcessInfo().getCode();
                })
                .distinct()
                .count() > 1;
        if (noSameProcess) {
            throw new BusinessException("只能合并主产物相同且工序相同的任务");
        }
    }

    /**
     * 合并任务
     *
     * @param taskAdjusts 待合并的任务集合
     * @return 合并任务以及影响的任务
     */
    private List<TaskDTO> doMergeTask(List<TaskDTO> taskAdjusts) {
        // 合并任务结果
        List<TaskDTO> planTaskAdjustList = new ArrayList<>();
        // 找到计划开始时间最早的任务，以该任务的计划开始时间作为合并任务的计划开始时间
        taskAdjusts.sort(Comparator.comparing(TaskDTO::getStartTime));
        TaskDTO firstTaskAdjust = taskAdjusts.get(0);
        // 合并任务
        TaskDTO mergeTask = null;
        // 合并任务的子任务
        List<TaskDTO> childTaskList = new ArrayList<>();
        boolean allOriginTask = taskAdjusts.stream()
                .map(task -> Optional.ofNullable(task.getOriginTaskCode()).orElseGet(task::getCode))
                .distinct()
                .count() == 1;
        if (firstTaskAdjust.getMergeTask()) {
            // 时间最早的任务本身是合并任务
            mergeTask = firstTaskAdjust;
            taskAdjusts.remove(0);
            childTaskList.addAll(taskAdjustFacadeService.listTaskByMergeTaskCode(mergeTask.getCode()).stream()
                    .filter(task -> !task.getDiscard())
                    .toList());
        } else if (allOriginTask) {
            // 所有待合并任务都来着于同一个任务
            mergeTask = firstTaskAdjust;
            taskAdjusts.remove(0);
            childTaskList.add(mergeTask);
        } else {
            // 创建新的合并任务
            mergeTask = buildMergeTask(firstTaskAdjust);
        }

        Queue<TaskDTO> childTaskQueue = new ArrayDeque<>();
        taskAdjusts.forEach(childTaskQueue::offer);
        while (!childTaskQueue.isEmpty()) {
            TaskDTO childTask = childTaskQueue.poll();
            childTask.setMainResourceId(mergeTask.getMainResourceId());
            // 若待合并的任务是已合并任务，则先将该任务的子任务拆出来，合并到新的合并任务
            if (childTask.getMergeTask()) {
                taskAdjustFacadeService.listTaskByMergeTaskCode(childTask.getCode()).stream()
                        .filter(task -> !task.getDiscard())
                        .forEach(childTaskQueue::offer);
                childTask.setDiscard(true);
                planTaskAdjustList.add(childTask);
                continue;
            }
            // 若合并任务的子任务与待合并的任务是同一个任务拆分而来的，或者任务编码相同，则合并为一个子任务
            Optional<TaskDTO> optionalSameOriginTask = childTaskList.stream()
                    .filter(task -> {
                        if (ObjectNull.isNull(childTask.getOriginTaskCode())) {
                            return childTask.getCode().equals(task.getOriginTaskCode());
                        }
                        return childTask.getOriginTaskCode().equals(task.getCode()) || childTask.getOriginTaskCode().equals(task.getOriginTaskCode());
                    })
                    .findFirst();
            if (optionalSameOriginTask.isPresent()) {
                TaskDTO sameTask = optionalSameOriginTask.get();
                BigDecimal sameQuantityCompleted = Optional.ofNullable(sameTask.getQuantityCompleted()).orElseGet(() -> BigDecimal.ZERO);
                BigDecimal childQuantityCompleted = Optional.ofNullable(childTask.getQuantityCompleted()).orElseGet(() -> BigDecimal.ZERO);
                sameTask
                        .setScheduledQuantity(sameTask.getScheduledQuantity().add(childTask.getScheduledQuantity()))
                        .setQuantityCompleted(sameQuantityCompleted.add(childQuantityCompleted))
                        .setMergeTask(false);
                childTask.setDiscard(true);
                childTask.setMergeTaskCode(null);
                planTaskAdjustList.add(childTask);
                if (!sameTask.getCode().equals(mergeTask.getCode())) {
                    planTaskAdjustList.add(sameTask);
                }
                continue;
            }

            // 合并子任务
            childTask
                    .setMergeTaskCode(mergeTask.getCode())
                    .setMergeTask(false)
                    .setDiscard(false)
                    .setCompliant(true);
            childTaskList.add(childTask);
            planTaskAdjustList.add(childTask);
        }

        // 重新计算合并任务
        calculateMergeTask(mergeTask, childTaskList);
        planTaskAdjustList.add(mergeTask);

        // 计算时间
        List<TaskDTO> taskAdjustList = taskAdjustFacadeService.listTasksWithAdjustsByResourceId(mergeTask.getMainResourceId(), mergeTask.getStartTime()).stream()
                .filter(task -> !task.getDiscard())
                .collect(Collectors.toList());
        List<String> codes = planTaskAdjustList.stream().map(TaskDTO::getCode).toList();
        // 排除被丢弃的任务和子任务
        taskAdjustList.removeIf(task -> codes.contains(task.getCode()) || !task.getDiscard());
        taskAdjustList.addAll(planTaskAdjustList.stream()
                .filter(task -> ObjectNull.isNull(task.getMergeTaskCode()))
                .filter(task -> !task.getDiscard())
                .toList());
        taskAdjustList.sort(Comparator.comparing(TaskDTO::getStartTime));
        // 合并后重新计算任务时间
        // 获取所有待修改时间的前置任务
        List<TaskDTO> frontTasks = listAdjustTaskFrontTasks(taskAdjustList);
        // 获取资源可用日历
        Set<String> resourceIds = frontTasks.stream().map(TaskDTO::getMainResourceId).collect(Collectors.toSet());
        resourceIds.add(mergeTask.getMainResourceId());
        Map<String, List<WorkCalendar>> workCalendarMap = workCalendarFacadeService.listResourceScheduleCalendar(resourceIds);
        calculateTaskTime(taskAdjustList, frontTasks, workCalendarMap);
        taskAdjustList.removeIf(task -> codes.contains(task.getCode()) || task.getDiscard());
        taskAdjustList.forEach(task -> task.setDiscard(false));
        planTaskAdjustList.addAll(taskAdjustList);

        // 修改子任务的计划开始时间，计划结束时间
        for (TaskDTO child : childTaskList) {
            child.setStartTime(mergeTask.getStartTime()).setEndTime(mergeTask.getEndTime());
        }
        planTaskAdjustList.addAll(childTaskList.stream().filter(task -> !codes.contains(task.getCode())).toList());

        return planTaskAdjustList;
    }


    /**
     * 重新计算合并任务
     *
     * @param mergeTask     合并任务
     * @param childTaskList 合并任务的子任务集合
     */
    private void calculateMergeTask(TaskDTO mergeTask, List<TaskDTO> childTaskList) {
        // 重新计算合并任务
        BigDecimal mergeScheduledQuantity = BigDecimal.ZERO;
        BigDecimal mergeQuantityCompleted = BigDecimal.ZERO;
        LocalDateTime mergeLastCompletionTime = null;
        List<PlanTaskInputMaterialDTO> mergeInputMaterials = new ArrayList<>();
        Set<String> mergeFrontTaskCodes = new HashSet<>();
        Set<String> mergeNextTaskCodes = new HashSet<>();
        for (TaskDTO childTask : childTaskList) {
            // 主产物计划生产数量
            mergeScheduledQuantity = mergeScheduledQuantity.add(childTask.getScheduledQuantity());
            // 已完成数量
            if (ObjectNull.isNotNull(childTask.getQuantityCompleted())) {
                mergeQuantityCompleted = mergeQuantityCompleted.add(childTask.getQuantityCompleted());
            }
            // 最近一次报工完成时间
            if (mergeQuantityCompleted.compareTo(BigDecimal.ZERO) > 0) {
                if (ObjectNull.isNotNull(childTask.getLastCompletionTime())) {
                    if (ObjectNull.isNull(mergeLastCompletionTime) || childTask.getLastCompletionTime().isAfter(mergeLastCompletionTime)) {
                        mergeLastCompletionTime = childTask.getLastCompletionTime();
                    }
                }
            }
            // 输入物料
            childTask.getInputMaterials().forEach(inputMaterial -> {
                Optional<PlanTaskInputMaterialDTO> optionalInputMaterial = mergeInputMaterials.stream()
                        .filter(material -> material.getId().equals(inputMaterial.getId()))
                        .findFirst();
                if (optionalInputMaterial.isPresent()) {
                    PlanTaskInputMaterialDTO materialDTO = optionalInputMaterial.get();
                    materialDTO.setQuantity(materialDTO.getQuantity().add(inputMaterial.getQuantity()));
                } else {
                    mergeInputMaterials.add(BeanCopyUtil.copy(inputMaterial, PlanTaskInputMaterialDTO.class));
                }
            });

            // 前置任务编码集合
            if (ObjectNull.isNotNull(childTask.getFrontTaskCodes())) {
                mergeFrontTaskCodes.addAll(childTask.getFrontTaskCodes());
            }
            if (ObjectNull.isNotNull(childTask.getNextTaskCodes())) {
                mergeNextTaskCodes.addAll(childTask.getNextTaskCodes());
            }
        }

        mergeTask
                .setScheduledQuantity(mergeScheduledQuantity)
                .setQuantityCompleted(mergeQuantityCompleted)
                .setLastCompletionTime(mergeLastCompletionTime)
                .setInputMaterials(mergeInputMaterials)
                .setFrontTaskCodes(mergeFrontTaskCodes)
                .setNextTaskCodes(mergeNextTaskCodes)
                .setCompliant(true)
                .setTaskStatus(PlanUtils.calculateTaskStatus(mergeTask.getScheduledQuantity(), mergeTask.getQuantityCompleted()));
    }


    /**
     * 构造合并任务
     *
     * @param firstTaskAdjust 开始时间最早的任务
     * @return 合并任务
     */
    private TaskDTO buildMergeTask(TaskDTO firstTaskAdjust) {
        String mergeTaskCode = PlanUtils.generateTaskCode(MERGE_TASK_CODE_PREFIX);
        TaskDTO mergeTask = new TaskDTO();
        mergeTask.setId(IdWorker.getIdStr());
        mergeTask.setTaskStatus(null);
        mergeTask.setDiscard(false);
        mergeTask.setScheduledQuantity(BigDecimal.ZERO);
        mergeTask.setQuantityCompleted(BigDecimal.ZERO);
        mergeTask.setCode(mergeTaskCode);
        mergeTask.setMainResourceId(firstTaskAdjust.getMainResourceId());
        mergeTask.setProcessInfo(firstTaskAdjust.getProcessInfo());
        mergeTask.setFrontTaskCodes(Collections.emptySet());
        mergeTask.setNextTaskCodes(Collections.emptySet());
        mergeTask.setStartTime(firstTaskAdjust.getStartTime());
        mergeTask.setPinned(false);
        mergeTask.setMergeTask(true);
        mergeTask.setSupplement(false);
        mergeTask.setColor(firstTaskAdjust.getColor());
        mergeTask.setInputMaterials(Collections.emptyList());
        mergeTask.setMergeTaskCode(null);
        mergeTask.setOriginTaskCode(null);
        mergeTask.setMainOrderId(null);
        mergeTask.setProductionOrderId(null);
        mergeTask.setEndTime(null);
        mergeTask.setLastCompletionTime(null);
        mergeTask.setStartTask(null);
        mergeTask.setEndTask(null);

        return mergeTask;
    }

    /**
     * 重新设置调整任务相关的任务关联关系
     *
     * @param taskList 调整任务集合
     * @return 关联干系
     */
    private List<TaskDTO> relationshipNextTaskCodes(List<TaskDTO> taskList) {
        Map<String, TaskDTO> taskAdjustMap = taskList.stream().collect(Collectors.toMap(TaskDTO::getCode, Function.identity()));

        Set<String> taskCodes = taskList.stream().filter(task -> ObjectNull.isNotNull(task.getFrontTaskCodes()))
                .flatMap(task -> task.getFrontTaskCodes().stream())
                .collect(Collectors.toSet());
        List<TaskDTO> frontTaskList = taskAdjustFacadeService.listTaskByCodes(taskCodes);

        List<TaskDTO> modifyfrontTaskList = new ArrayList<>();
        frontTaskList.forEach(frontTask -> {
            for (TaskDTO task : taskList) {
                if (ObjectNull.isNull(task.getFrontTaskCodes()) || !task.getFrontTaskCodes().contains(frontTask.getCode())) {
                    continue;
                }
                if (task.getDiscard() || ObjectNull.isNull(task.getMergeTaskCode())) {
                    frontTask.getNextTaskCodes().remove(task.getCode());
                } else {
                    frontTask.getNextTaskCodes().add(task.getCode());
                }
            }
            TaskDTO mergeTask = taskAdjustMap.get(frontTask.getCode());
            if (ObjectNull.isNotNull(mergeTask)) {
                mergeTask.setNextTaskCodes(frontTask.getNextTaskCodes());
            } else {
                modifyfrontTaskList.add(frontTask);
            }
        });

        taskList.addAll(modifyfrontTaskList);

        return taskList;
    }

    /**
     * 将结果转换为前端展示的数据结构
     *
     * @param taskList 本次合并重新计算的所有任务
     * @return 转换后的任务
     */
    private TaskAdjustMergeVO convertTaskAdjustToVo(List<TaskDTO> taskList) {
        List<ResourceGanttTaskVO> ganttTaskList = convertResourceGanttTaskList(taskList);
        List<String> removeTaskCodes = taskList.stream()
                .filter(TaskDTO::getDiscard)
                .map(TaskDTO::getCode)
                .toList();
        return new TaskAdjustMergeVO().setRefreshPartialTasks(ganttTaskList).setRemoveTaskCodes(removeTaskCodes);
    }
}
