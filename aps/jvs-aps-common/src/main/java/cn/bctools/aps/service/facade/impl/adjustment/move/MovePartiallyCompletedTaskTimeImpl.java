package cn.bctools.aps.service.facade.impl.adjustment.move;

import cn.bctools.aps.annotation.AdjustTaskMove;
import cn.bctools.aps.dto.schedule.TaskDTO;
import cn.bctools.aps.dto.schedule.adjustment.MovePartiallyCompletedTaskTimeDTO;
import cn.bctools.aps.entity.enums.PlanningDirectionEnum;
import cn.bctools.aps.enums.TaskMoveTypeEnum;
import cn.bctools.aps.solve.model.WorkCalendar;
import cn.bctools.aps.solve.util.TaskCalendarUtils;
import cn.bctools.aps.solve.util.TaskDurationUtils;
import cn.bctools.common.exception.BusinessException;
import cn.bctools.common.utils.BeanCopyUtil;
import cn.bctools.common.utils.ObjectNull;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * @author jvs
 * 移动部分完成的任务进度到指定时间
 */
@AdjustTaskMove(type = TaskMoveTypeEnum.MOVE_PARTIALLY_COMPLETED_TASK_TIME)
@Service
@AllArgsConstructor
public class MovePartiallyCompletedTaskTimeImpl extends AbstractAdjustTaskMove<MovePartiallyCompletedTaskTimeDTO> {

    @Override
    protected List<TaskDTO> moveTask(MovePartiallyCompletedTaskTimeDTO input) {
        // 查询所有部分完成的任务
        List<TaskDTO> taskAdjustList = taskAdjustFacadeService.listPartiallyCompletedTask().stream()
                // 排除合并任务的子任务
                .filter(task -> ObjectNull.isNull(task.getMergeTaskCode()))
                // 排除已锁定的任务（已锁定的任务，需要解锁后才能调整）
                .filter(task -> !task.getPinned())
                // 排除已丢弃的任务
                .filter(task -> !task.getDiscard())
                .collect(Collectors.toList());
        if (ObjectNull.isNull(taskAdjustList)) {
            throw new BusinessException("未找到可移动的任务");
        }

        // 从所有部分完成的任务中，每个资源保留开始时间最早的一个任务，用于移动到指定时间
        taskAdjustList = taskAdjustList.stream()
                .collect(Collectors.groupingBy(TaskDTO::getMainResourceId, Collectors.minBy(Comparator.comparing(TaskDTO::getStartTime))))
                .values()
                .stream()
                .filter(Optional::isPresent)
                .map(Optional::get)
                .collect(Collectors.toList());

        // 将部分完成的任务进度移动到指定时间，重新计算这部分任务的起止时间
        List<String> resourceIds = taskAdjustList.stream().map(TaskDTO::getMainResourceId).toList();
        // 获取资源可用日历
        Map<String, List<WorkCalendar>> workCalendarMap = workCalendarFacadeService.listResourceScheduleCalendar(resourceIds);
        List<TaskDTO> calculatePartiallyCompletedTasks = calculatePartiallyCompletedTaskTime(input.getTime(), taskAdjustList, workCalendarMap);
        if (ObjectNull.isNull(calculatePartiallyCompletedTasks)) {
            return Collections.emptyList();
        }

        // 变更资源任务后，继续计算影响到的后续任务
        return calculateResourceTask(calculatePartiallyCompletedTasks, workCalendarMap);
    }

    /**
     * 将部分完成的任务进度移动到指定时间，重新计算这部分任务的起止时间
     * <p>
     *    报工进度预计的持续工作时间，对准指定时间，从而调整任务的开始时间和结束时间
     *
     * @param time 指定时间
     * @param partiallyCompletedTasks 待调整的任务集合
     * @param workCalendarMap Map<资源id, 工作日历集合>
     * @return 改变了计划时间的任务集合
     */
    private List<TaskDTO> calculatePartiallyCompletedTaskTime(LocalDateTime time, List<TaskDTO> partiallyCompletedTasks, Map<String, List<WorkCalendar>> workCalendarMap) {
        List<TaskDTO> changeTaskList = new ArrayList<>();
        for (TaskDTO task : partiallyCompletedTasks) {
            // 计算报工部分理论结束时间
            List<WorkCalendar> resourceWorkCalendars = workCalendarMap.get(task.getMainResourceId());
            Duration partiallyCompletedDuration = TaskDurationUtils.calculateTaskDuration(task.getMainResourceId(), task.getProcessInfo(), task.getQuantityCompleted());
            LocalDateTime partiallyCompletedEndTime = TaskCalendarUtils.calculateEndTime(task.getStartTime(), partiallyCompletedDuration, resourceWorkCalendars);
            Duration duration = Duration.between(partiallyCompletedEndTime, time);
            if (duration.compareTo(Duration.ZERO) <= 0) {
                continue;
            }
            // 重新计算任务的开始时间
            LocalDateTime earliestStartTime = task.getStartTime().plus(duration);
            LocalDateTime startTime = TaskCalendarUtils.calculateStartTime(earliestStartTime, resourceWorkCalendars, PlanningDirectionEnum.FORWARD);
            task.setStartTime(startTime);
            // 重新计算任务的结束时间
            Duration taskDuration = TaskDurationUtils.calculateTaskDuration(task.getMainResourceId(), task.getProcessInfo(), task.getScheduledQuantity());
            LocalDateTime endTime = TaskCalendarUtils.calculateEndTime(task.getStartTime(), taskDuration, resourceWorkCalendars);
            task.setEndTime(endTime);

            // 加入已变更集合
            changeTaskList.add(task);
        }
        return changeTaskList;
    }


    /**
     * 计算变更任务所在资源的后续任务
     *
     * @param calculatePartiallyCompletedTasks 变更任务集合（一个资源一个任务）
     * @param workCalendarMap Map<资源id, 工作日历集合>
     * @return 所有变更的任务
     */
    private List<TaskDTO> calculateResourceTask(List<TaskDTO> calculatePartiallyCompletedTasks, Map<String, List<WorkCalendar>> workCalendarMap) {
        List<TaskDTO> calculateTasks = new ArrayList<>();
        // 获取已变更任务所属资源所有未完成的任务
        List<String> changeResourceIds = calculatePartiallyCompletedTasks.stream().map(TaskDTO::getMainResourceId).toList();
        Map<String, List<TaskDTO>> resourceTaskMap = taskAdjustFacadeService.listTasksWithAdjustsByResourceIds(changeResourceIds).stream()
                .filter(task -> ObjectNull.isNull(task.getMergeTaskCode()))
                .filter(task -> !task.getDiscard())
                .collect(Collectors.groupingBy(TaskDTO::getMainResourceId));

        // 筛选每个资源需要重新计算的任务
        for (TaskDTO calculatePartiallyCompletedTask : calculatePartiallyCompletedTasks) {
            String resourceId = calculatePartiallyCompletedTask.getMainResourceId();
            // 获取当前任务所在资源未完成任务
            List<TaskDTO> resourceTasks = resourceTaskMap.get(resourceId);
            if (ObjectNull.isNull(resourceTasks)) {
                continue;
            }
            // 调整前的任务
            Optional<TaskDTO> optionalOriginalTask = resourceTasks.stream().filter(task -> task.getCode().equals(calculatePartiallyCompletedTask.getCode())).findFirst();
            TaskDTO originalTask = optionalOriginalTask.orElse(null);
            if (ObjectNull.isNull(originalTask)) {
                continue;
            }

            // 筛选调整前的任务所在资源之后的所有任务
            resourceTasks = resourceTasks.stream()
                    .filter(task -> task.getStartTime().isAfter(originalTask.getStartTime()))
                    .collect(Collectors.toList());

            // 获取所有待修改时间的前置任务
            Set<String> frontCodes = resourceTasks.stream()
                    .filter(task -> ObjectNull.isNotNull(task.getFrontTaskCodes()))
                    .flatMap(task -> task.getFrontTaskCodes().stream())
                    .collect(Collectors.toSet());
            List<TaskDTO> frontTasks = resourceTaskMap.values()
                    .stream()
                    .flatMap(Collection::stream)
                    .filter(task -> frontCodes.contains(task.getCode()))
                    .filter(task -> ObjectNull.isNull(task.getMergeTaskCode()))
                    .map(task -> BeanCopyUtil.copy(task, TaskDTO.class))
                    .toList();


            // 将被移动的任务加入待计算集合
            resourceTasks.add(calculatePartiallyCompletedTask);
            // 按计划开始时间顺序排序
            Comparator<TaskDTO> comparing = Comparator.comparing(task -> !task.getCode().equals(calculatePartiallyCompletedTask.getCode()));
            comparing = comparing.thenComparing(TaskDTO::getStartTime);
            resourceTasks.sort(comparing);
            // 计算开始时间
            calculateTaskTime(resourceTasks, frontTasks, workCalendarMap);

            // 重新计算时间后，修改合并任务的子任务的计划时间
            updateMergeChildTaskTime(resourceTasks);

            // 加入重新计算时间任务集合
            calculateTasks.addAll(resourceTasks);
        }

        return calculateTasks;
    }

}
