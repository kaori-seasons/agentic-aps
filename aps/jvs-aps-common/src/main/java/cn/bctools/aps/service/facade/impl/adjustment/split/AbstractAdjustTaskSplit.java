package cn.bctools.aps.service.facade.impl.adjustment.split;

import cn.bctools.aps.dto.schedule.AdjustTaskSplitDTO;
import cn.bctools.aps.dto.schedule.TaskDTO;
import cn.bctools.aps.entity.PlanTaskAdjustPO;
import cn.bctools.aps.entity.PlanTaskPO;
import cn.bctools.aps.entity.ProductionResourcePO;
import cn.bctools.aps.entity.dto.plan.PlanTaskInputMaterialDTO;
import cn.bctools.aps.entity.enums.PlanTaskStatusEnum;
import cn.bctools.aps.service.PlanTaskAdjustService;
import cn.bctools.aps.service.PlanTaskService;
import cn.bctools.aps.service.facade.AdjustTaskSplitFacadeService;
import cn.bctools.aps.service.facade.impl.adjustment.AbstractAdjustTask;
import cn.bctools.aps.solve.model.WorkCalendar;
import cn.bctools.aps.solve.util.TaskCalendarUtils;
import cn.bctools.aps.solve.util.TaskDurationUtils;
import cn.bctools.aps.util.PlanUtils;
import cn.bctools.aps.vo.schedule.TaskAdjustSplitVO;
import cn.bctools.aps.vo.schedule.report.ResourceGanttTaskVO;
import cn.bctools.common.exception.BusinessException;
import cn.bctools.common.utils.BeanCopyUtil;
import cn.bctools.common.utils.ObjectNull;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * @author jvs
 * 任务拆分模板方法
 */
public abstract class AbstractAdjustTaskSplit<T extends AdjustTaskSplitDTO> extends AbstractAdjustTask implements AdjustTaskSplitFacadeService<T> {

    @Resource
    private PlanTaskService planTaskService;
    @Resource
    private PlanTaskAdjustService planTaskAdjustService;

    @Override
    public TaskAdjustSplitVO split(T input) {
        // 拆分任务
        List<TaskDTO> subTaskList = getSubTasks(input);
        if (ObjectNull.isNull(subTaskList)) {
            return null;
        }

        // 重新设置调整任务相关的任务关联关系
        List<TaskDTO> taskAdjustList = relationshipNextTaskCodes(subTaskList);

        // 保存调整后的任务
        taskAdjustFacadeService.saveAdjustTasks(taskAdjustList);

        // 返回将拆分任务结果转换为前端展示的数据结构
        return convertTaskAdjustToVo(taskAdjustList);
    }

    /**
     * 获取拆分后的任务
     *
     * @param input 拆分参数
     * @return 拆分后的任务
     */
    protected abstract List<TaskDTO> getSubTasks(T input);


    /**
     * 通用的拆分校验
     *
     * @param splitTask 待拆分的任务
     */
    protected void checkCanSplit(TaskDTO splitTask) {
        if (ObjectNull.isNotNull(splitTask.getMergeTaskCode())) {
            throw new BusinessException("不能拆分合并任务的子任务");
        }
        if (PlanTaskStatusEnum.COMPLETED.equals(splitTask.getTaskStatus())) {
            throw new BusinessException("不能拆分已完成的任务");
        }
        if (splitTask.getPinned()) {
            throw new BusinessException("不能拆分已锁定的任务");
        }
        if (splitTask.getDiscard()) {
            throw new BusinessException("不能拆分已丢弃的任务");
        }
    }

    /**
     * 生成子任务
     *
     * @param splitTask                待拆分任务
     * @param subScheduledQuantityList 待生成的子任务计划生产数量
     * @return 拆分后的任务
     */
    protected List<TaskDTO> splitTask(TaskDTO splitTask, List<BigDecimal> subScheduledQuantityList) {
        // 拆分任务
        List<TaskDTO> splitResultTasks = generateSubTasks(splitTask, subScheduledQuantityList);
        if (!splitTask.getMergeTask()) {
            return splitResultTasks;
        }
        return splitMergeTask(splitTask, splitResultTasks);
    }

    /**
     * 生成子任务
     *
     * @param splitTask                待拆分任务
     * @param subScheduledQuantityList 待生成的子任务计划生产数量
     * @return 拆分后的任务
     */
    private List<TaskDTO> generateSubTasks(TaskDTO splitTask, List<BigDecimal> subScheduledQuantityList) {
        List<TaskDTO> taskAdjustList = new ArrayList<>();
        String originTaskCode = Optional.ofNullable(splitTask.getOriginTaskCode()).orElseGet(splitTask::getCode);
        ProductionResourcePO resource = productionResourceService.getById(splitTask.getMainResourceId());
        List<WorkCalendar> workCalendarList = workCalendarFacadeService.getResourceScheduleCalendar(resource.getId());
        BigDecimal splitTaskTotalQuantityCompleted = Optional.ofNullable(splitTask.getQuantityCompleted()).orElse(BigDecimal.ZERO);
        // 前一个子任务的预计结束时间
        LocalDateTime previousTaskEndTime = null;
        for (BigDecimal subScheduledQuantity : subScheduledQuantityList) {
            TaskDTO subTask = BeanCopyUtil.copy(splitTask, TaskDTO.class);
            LocalDateTime subTaskStartTime = Optional.ofNullable(previousTaskEndTime).orElseGet(subTask::getStartTime);
            // 计算待拆分任务持续时长
            Duration taskDuration = TaskDurationUtils.calculateTaskDuration(resource.getId(), splitTask.getProcessInfo(), subScheduledQuantity);
            // 重新计算待拆分任务的结束时间
            LocalDateTime subTaskEndTime = TaskCalendarUtils.calculateEndTime(subTaskStartTime, taskDuration, workCalendarList);
            // 计算待拆任务的拆分后已完成数量
            BigDecimal subTaskQuantityCompletedSubtract = BigDecimal.ZERO;
            if (splitTaskTotalQuantityCompleted.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal subTaskTotalQuantityCompleted = splitTaskTotalQuantityCompleted.subtract(subScheduledQuantity);
                if (subTaskTotalQuantityCompleted.compareTo(BigDecimal.ZERO) >= 0) {
                    splitTaskTotalQuantityCompleted = subTaskTotalQuantityCompleted;
                    subTaskQuantityCompletedSubtract = subScheduledQuantity;
                } else {
                    subTaskQuantityCompletedSubtract = splitTaskTotalQuantityCompleted;
                    splitTaskTotalQuantityCompleted = BigDecimal.ZERO;
                }
            }

            // 计算子任务输入物料
            List<PlanTaskInputMaterialDTO> inputMaterials = PlanUtils.getInputMaterial(subScheduledQuantity, subTask.getProcessInfo());
            String subTaskCode;
            if (ObjectNull.isNull(previousTaskEndTime)) {
                subTaskCode = subTask.getCode();
            } else {
                subTaskCode = PlanUtils.generateTaskCode(splitTask.getMergeTask() ? MERGE_TASK_CODE_PREFIX : SPLIT_TASK_CODE_PREFIX);
            }
            subTask
                    .setId(IdWorker.getIdStr())
                    .setCode(subTaskCode)
                    .setStartTime(subTaskStartTime)
                    .setEndTime(subTaskEndTime)
                    .setScheduledQuantity(subScheduledQuantity)
                    .setInputMaterials(inputMaterials)
                    .setDiscard(false)
                    .setCompliant(true)
                    .setQuantityCompleted(subTaskQuantityCompletedSubtract);
            if (!subTaskCode.equals(originTaskCode) && !splitTask.getMergeTask()) {
                subTask.setOriginTaskCode(originTaskCode);
            }
            subTask.setTaskStatus(PlanUtils.calculateTaskStatus(subTask.getScheduledQuantity(), subTaskQuantityCompletedSubtract));
            if (PlanTaskStatusEnum.PENDING.equals(subTask.getTaskStatus())) {
                subTask.setLastCompletionTime(null);
            }
            taskAdjustList.add(subTask);
            previousTaskEndTime = subTaskEndTime;
        }
        return taskAdjustList;
    }


    /**
     * 进一步拆分合并任务
     * <p>
     * 将合并任务的子任务拆分到多个合并任务下
     *
     * @param splitMergeTask 待拆分的合并任务
     * @param subTasks       拆分后的合并任务
     * @return 拆分后的任务
     */
    private List<TaskDTO> splitMergeTask(TaskDTO splitMergeTask, List<TaskDTO> subTasks) {
        // 查询合并任务的子任务，并按计划数量倒序排序
        List<TaskDTO> childTasks = taskAdjustFacadeService.listTaskByMergeTaskCode(splitMergeTask.getCode());
        Comparator<TaskDTO> childTaskComparator =
                Comparator.comparing(TaskDTO::getQuantityCompleted, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(Comparator.comparing(TaskDTO::getScheduledQuantity).reversed());
        childTasks.sort(childTaskComparator);
        // 拆分后的合并任务，按计划开始时间顺序排序
        subTasks.sort(Comparator.comparing(TaskDTO::getStartTime));
        // 优先先分配计划数量大的
        List<TaskDTO> newChildTasks = new ArrayList<>();
        subTasks.forEach(subMergeTask -> {
            BigDecimal mergeScheduledQuantity = subMergeTask.getScheduledQuantity();
            BigDecimal mergeQuantityCompleted = BigDecimal.ZERO;
            List<TaskDTO> currentMergeChildTasks = new ArrayList<>();
            for (TaskDTO childTask : childTasks) {
                if (mergeScheduledQuantity.compareTo(BigDecimal.ZERO) <= 0) {
                    break;
                }
                if (childTask.getScheduledQuantity().compareTo(BigDecimal.ZERO) == 0) {
                    continue;
                }
                // 分配计划数量
                BigDecimal childScheduledQuantity = childTask.getScheduledQuantity();
                BigDecimal subMergeScheduledQuantity = mergeScheduledQuantity.subtract(childScheduledQuantity);
                BigDecimal newChildScheduledQuantity = null;
                if (subMergeScheduledQuantity.compareTo(BigDecimal.ZERO) >= 0) {
                    mergeScheduledQuantity = subMergeScheduledQuantity;
                    newChildScheduledQuantity = childScheduledQuantity;
                    childScheduledQuantity = BigDecimal.ZERO;
                } else {
                    newChildScheduledQuantity = mergeScheduledQuantity;
                    mergeScheduledQuantity = BigDecimal.ZERO;
                    childScheduledQuantity = subMergeScheduledQuantity.abs();
                }
                // 分配子订单的已完成数量，更新合并订单的已完成数量
                BigDecimal childQuantityCompleted = Optional.ofNullable(childTask.getQuantityCompleted()).orElseGet(() -> BigDecimal.ZERO);
                BigDecimal newChildQuantityCompleted = BigDecimal.ZERO;
                if (childQuantityCompleted.compareTo(BigDecimal.ZERO) > 0) {
                    BigDecimal subMergeQuantityCompleted = childQuantityCompleted.subtract(newChildScheduledQuantity);
                    if (subMergeQuantityCompleted.compareTo(BigDecimal.ZERO) >= 0) {
                        childQuantityCompleted = subMergeQuantityCompleted;
                        newChildQuantityCompleted = newChildScheduledQuantity;
                    } else {
                        newChildQuantityCompleted = childQuantityCompleted;
                        childQuantityCompleted = BigDecimal.ZERO;
                    }
                }

                childTask
                        .setDiscard(true)
                        .setScheduledQuantity(childScheduledQuantity)
                        .setQuantityCompleted(childQuantityCompleted);

                // 生成新的子任务
                if (newChildScheduledQuantity.compareTo(BigDecimal.ZERO) > 0) {
                    String originTaskCode = Optional.ofNullable(childTask.getOriginTaskCode()).orElseGet(childTask::getCode);
                    String splitTaskCode = PlanUtils.generateTaskCode(SPLIT_TASK_CODE_PREFIX);
                    TaskDTO newChildTask = BeanCopyUtil.copy(childTask, TaskDTO.class)
                            .setId(IdWorker.getIdStr())
                            .setDiscard(false)
                            .setCode(splitTaskCode)
                            .setMergeTaskCode(subMergeTask.getCode())
                            .setOriginTaskCode(originTaskCode)
                            .setStartTime(subMergeTask.getStartTime())
                            .setEndTime(subMergeTask.getEndTime())
                            .setScheduledQuantity(newChildScheduledQuantity)
                            .setQuantityCompleted(newChildQuantityCompleted)
                            .setTaskStatus(PlanUtils.calculateTaskStatus(newChildScheduledQuantity, newChildQuantityCompleted));
                    currentMergeChildTasks.add(newChildTask);
                    // 计算合并订单的已完成数量
                    mergeQuantityCompleted = mergeQuantityCompleted.add(newChildQuantityCompleted);
                }
            }
            // 若合并任务只有一个子任务，则丢弃该合并任务，且设置该合并任务的子任务为非合并任务
            if (currentMergeChildTasks.size() == 1) {
                currentMergeChildTasks.get(0)
                        .setMergeTaskCode(null)
                        .setMergeTask(false);
                subMergeTask.setDiscard(true);
            }
            newChildTasks.addAll(currentMergeChildTasks);
            // 修改合并任务状态
            subMergeTask.setTaskStatus(PlanUtils.calculateTaskStatus(subMergeTask.getScheduledQuantity(), mergeQuantityCompleted));
        });

        subTasks.addAll(newChildTasks);
        subTasks.addAll(childTasks);
        return subTasks;
    }

    /**
     * 重新设置调整任务相关的任务关联关系
     *
     * @param taskList 调整任务集合
     * @return 关联干系
     */
    private List<TaskDTO> relationshipNextTaskCodes(List<TaskDTO> taskList) {
        // 获取未重新排产的所有子任务
        Set<String> originTaskCodeList = taskList.stream()
                // 排除合并任务的子任务
                .filter(task -> ObjectNull.isNull(task.getMergeTaskCode()))
                .map(subTask -> Optional.ofNullable(subTask.getOriginTaskCode()).orElseGet(subTask::getCode))
                .collect(Collectors.toSet());
        List<TaskDTO> originTaskAdjustTaskList = BeanCopyUtil.copys(planTaskAdjustService.listSubTaskByCodes(originTaskCodeList), TaskDTO.class);
        List<String> adjustCodes = taskList.stream().map(TaskDTO::getCode).toList();
        originTaskAdjustTaskList.removeIf(task -> adjustCodes.contains(task.getCode()));
        originTaskAdjustTaskList.addAll(taskList);

        // 转换为前端可显示的数据结构
        List<TaskDTO> tasks = new ArrayList<>();
        Set<String> frontTaskCodes = new HashSet<>();
        originTaskAdjustTaskList.forEach(subTask -> {
            tasks.add(subTask);
            if (ObjectNull.isNotNull(subTask.getFrontTaskCodes())) {
                frontTaskCodes.addAll(subTask.getFrontTaskCodes());
            }
        });

        // 解析子任务相关任务的关联关系。找到源任务的前置任务的后置任务需要添加子任务
        List<TaskDTO> frontAllTasks = new ArrayList<>();
        List<PlanTaskAdjustPO> frontPlanTaskAdjusts = planTaskAdjustService.listSubTaskByCodes(frontTaskCodes);
        List<PlanTaskPO> frontTasks = planTaskService.listSubTaskByCodes(frontTaskCodes);
        if (ObjectNull.isNotNull(frontPlanTaskAdjusts)) {
            frontAllTasks.addAll(BeanCopyUtil.copys(frontPlanTaskAdjusts, TaskDTO.class));
        }
        if (ObjectNull.isNotNull(frontTasks)) {
            frontTasks.removeIf(task -> frontPlanTaskAdjusts.stream().anyMatch(planTask -> planTask.getCode().equals(task.getCode())));
            frontAllTasks.addAll(BeanCopyUtil.copys(frontTasks, TaskDTO.class));
        }
        Set<String> adjustTaskCodes = taskList.stream().map(TaskDTO::getCode).collect(Collectors.toSet());
        frontAllTasks.removeIf(task -> adjustTaskCodes.contains(task.getCode()));
        frontAllTasks.forEach(frontTask -> {
            Set<String> nextTaskCodes = tasks.stream()
                    .filter(task -> task.getFrontTaskCodes().contains(frontTask.getCode()) || task.getFrontTaskCodes().contains(frontTask.getOriginTaskCode()))
                    .map(TaskDTO::getCode)
                    .collect(Collectors.toSet());
            frontTask.getNextTaskCodes().addAll(nextTaskCodes);
            if (ObjectNull.isNull(frontTask.getDiscard())) {
                frontTask.setDiscard(false);
            }
            if (ObjectNull.isNull(frontTask.getCompliant())) {
                frontTask.setCompliant(true);
            }
        });
        taskList.addAll(frontAllTasks);
        return taskList;
    }


    /**
     * 将拆分任务结果转换为前端展示的数据结构
     *
     * @param subTaskList 本次拆分得到的子任务集合
     * @return 转换后的任务
     */
    private TaskAdjustSplitVO convertTaskAdjustToVo(List<TaskDTO> subTaskList) {
        // 获取未重新排产的所有子任务
        Set<String> originTaskCodeList = subTaskList.stream()
                .map(subTask -> Optional.ofNullable(subTask.getOriginTaskCode()).orElseGet(subTask::getCode))
                .collect(Collectors.toSet());
        List<PlanTaskAdjustPO> originTaskAdjustTaskList = planTaskAdjustService.listSubTaskByCodes(originTaskCodeList).stream()
                .filter(task -> !task.getDiscard())
                .collect(Collectors.toList());

        // 转换为前端可显示的数据结构
        List<TaskDTO> tasks = BeanCopyUtil.copys(originTaskAdjustTaskList, TaskDTO.class);
        List<ResourceGanttTaskVO> ganttTaskList = convertResourceGanttTaskList(tasks);
        return new TaskAdjustSplitVO().setRefreshPartialTasks(ganttTaskList);
    }
}
