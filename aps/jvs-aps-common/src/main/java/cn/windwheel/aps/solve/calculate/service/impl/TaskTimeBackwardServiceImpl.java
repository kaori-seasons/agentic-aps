package cn.windwheel.aps.solve.calculate.service.impl;

import cn.windwheel.aps.annotation.PlanningDirection;
import cn.windwheel.aps.entity.dto.ProcessUseMainResourcesDTO;
import cn.windwheel.aps.entity.enums.PlanningDirectionEnum;
import cn.windwheel.aps.entity.enums.ProcessRelationshipEnum;
import cn.windwheel.aps.solve.calculate.service.TaskTimeService;
import cn.windwheel.aps.solve.calculate.service.dto.PendingChangeTaskDTO;
import cn.windwheel.aps.solve.model.MainProductionResource;
import cn.windwheel.aps.solve.model.ProductionTask;
import cn.windwheel.aps.solve.model.SchedulingSolution;
import cn.windwheel.aps.solve.model.WorkCalendar;
import cn.windwheel.aps.solve.util.DurationUtils;
import cn.windwheel.aps.solve.util.TaskCalendarUtils;
import cn.windwheel.common.utils.ObjectNull;
import lombok.extern.slf4j.Slf4j;
import org.optaplanner.core.api.score.director.ScoreDirector;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.IntStream;

/**
 * @author jvs
 * 计算任务时间 —— 逆排
 * <p>
 * 从交期往前安排任务
 */
@PlanningDirection(direction = PlanningDirectionEnum.BACKWARD)
@Slf4j
@Service
public class TaskTimeBackwardServiceImpl implements TaskTimeService {

    @Override
    public void updateTaskStartTime(ScoreDirector<SchedulingSolution> scoreDirector, MainProductionResource resource, int fromIndex) {
        LocalDateTime planEndTime = Optional.ofNullable(scoreDirector.getWorkingSolution().getPlanTime())
                .orElseGet(LocalDateTime::now);
        List<ProductionTask> allTasks = scoreDirector.getWorkingSolution().getTasks();
        Set<String> historyQueueTaskIds = new HashSet<>();
        Queue<PendingChangeTaskDTO> uncheckedPredecessorQueue = new ArrayDeque<>();
        PendingChangeTaskDTO pendingChangeTask = new PendingChangeTaskDTO()
                .setResource(resource)
                .setFromIndex(fromIndex);
        uncheckedPredecessorQueue.add(pendingChangeTask);
        while (!uncheckedPredecessorQueue.isEmpty()) {
            PendingChangeTaskDTO update = uncheckedPredecessorQueue.remove();
            List<ProductionTask> resourceTasks = update.getResource().getTaskList();
            if (ObjectNull.isNull(resourceTasks)) {
                continue;
            }
            LocalDateTime resourceNextStartTime = planEndTime;
            for (int idx = resourceTasks.size() - 1; idx >= 0; idx--) {
                ProductionTask task = resourceTasks.get(idx);
                historyQueueTaskIds.add(task.getId());
                if (task.getPinned()) {
                    if (ObjectNull.isNotNull(task.getStartTime())) {
                        resourceNextStartTime = task.getStartTime();
                    }
                    continue;
                }

                LocalDateTime startTime = calculateStartTime(update.getResource(), task, resourceNextStartTime, allTasks);
                scoreDirector.beforeVariableChanged(task, "startTime");
                task.setStartTime(startTime);
                scoreDirector.afterVariableChanged(task, "startTime");
                if (ObjectNull.isNotNull(startTime)) {
                    resourceNextStartTime = startTime;
                }
                if (ObjectNull.isNull(task.getFrontTaskCodes())) {
                    continue;
                }
                scoreDirector.getWorkingSolution().getTasks()
                        .stream()
                        .filter(j -> ObjectNull.isNull(j.getMergeTaskCode()))
                        .filter(j -> task.getFrontTaskCodes().contains(j.getCode()))
                        .filter(j -> j.getResource() != null)
                        .forEach(frontTask -> {
                            if (!historyQueueTaskIds.contains(frontTask.getId())) {
                                List<ProductionTask> frontResourceTasks = frontTask.getResource().getTaskList();
                                int frontTaskIndex = IntStream.range(0, frontResourceTasks.size())
                                        .filter(i -> frontResourceTasks.get(i).getId().equals(frontTask.getId()))
                                        .findFirst()
                                        .orElse(-1);
                                if (frontTaskIndex >= 0) {
                                    PendingChangeTaskDTO updateFrontTask = new PendingChangeTaskDTO()
                                            .setResource(frontTask.getResource())
                                            .setFromIndex(frontTaskIndex);
                                    historyQueueTaskIds.add(frontTask.getId());
                                    uncheckedPredecessorQueue.add(updateFrontTask);
                                }
                            }
                        });
            }
        }
    }

    @Override
    public LocalDateTime calculateStartTime(ProductionTask task, ProductionTask resourcePreviousTask, LocalDateTime resourcePreviousEndTime, List<ProductionTask> tasks, List<WorkCalendar> workCalendars, List<ProductionTask> currentResourcePreviousTasks) {
        if (ObjectNull.isNull(task) || ObjectNull.isNull(resourcePreviousEndTime)) {
            return null;
        }
        Duration taskDuration = task.calculateTaskDuration();
        if (ObjectNull.isNull(taskDuration)) {
            return null;
        }
        return TaskCalendarUtils.calculateBackwardStartTime(resourcePreviousEndTime, taskDuration, workCalendars);
    }

    /**
     * 计算任务开始时间
     *
     * @param resource              资源
     * @param task                  任务
     * @param resourceNextStartTime 当前任务所在资源后一个任务的开始时间
     * @param tasks                 所有任务
     * @return 任务开始时间
     */
    private LocalDateTime calculateStartTime(MainProductionResource resource, ProductionTask task, LocalDateTime resourceNextStartTime, List<ProductionTask> tasks) {
        Optional<ProcessUseMainResourcesDTO> processUseMainResources = task.getProcess().getUseMainResources()
                .stream()
                .filter(res -> res.getId().equals(resource.getId()))
                .findFirst();
        if (!processUseMainResources.isPresent()) {
            return null;
        }
        LocalDateTime latestEndTime = calculateLatestEndTime(task, resourceNextStartTime, tasks);
        if (ObjectNull.isNull(latestEndTime)) {
            return null;
        }
        Duration taskDuration = task.calculateTaskDuration();
        if (ObjectNull.isNull(taskDuration)) {
            return null;
        }
        LocalDateTime startTime = TaskCalendarUtils.calculateBackwardStartTime(latestEndTime, taskDuration, resource.getWorkCalendars());
        if (ObjectNull.isNull(startTime)) {
            return null;
        }

        // 与锁定任务冲突时，继续向前挪动
        List<ProductionTask> resourcePinnedTasks = resource.getTaskList().stream()
                .filter(ProductionTask::getPinned)
                .filter(t -> !t.getId().equals(task.getId()))
                .toList();
        if (ObjectNull.isNull(resourcePinnedTasks)) {
            return startTime;
        }
        task.setStartTime(startTime);
        Optional<LocalDateTime> minOverlappingStartTime = getMinOverlappingPinnedTaskStartTime(task, resourcePinnedTasks);
        while (minOverlappingStartTime.isPresent()) {
            startTime = TaskCalendarUtils.calculateBackwardStartTime(minOverlappingStartTime.get(), taskDuration, resource.getWorkCalendars());
            if (ObjectNull.isNull(startTime)) {
                return null;
            }
            task.setStartTime(startTime);
            minOverlappingStartTime = getMinOverlappingPinnedTaskStartTime(task, resourcePinnedTasks);
        }
        return startTime;
    }

    /**
     * 获取与当前任务有重叠的锁定任务中，最早开始时间
     *
     * @param task                当前任务
     * @param resourcePinnedTasks 资源上的锁定任务
     * @return 最早开始时间
     */
    private Optional<LocalDateTime> getMinOverlappingPinnedTaskStartTime(ProductionTask task, List<ProductionTask> resourcePinnedTasks) {
        if (ObjectNull.isNull(task.getStartTime()) || ObjectNull.isNull(task.getEndTime())) {
            return Optional.empty();
        }
        return resourcePinnedTasks.stream()
                .filter(t -> ObjectNull.isNotNull(t.getStartTime()) && ObjectNull.isNotNull(t.getEndTime()))
                .filter(t -> t.getEndTime().isAfter(task.getStartTime()) && t.getStartTime().isBefore(task.getEndTime()))
                .map(ProductionTask::getStartTime)
                .min(Comparator.naturalOrder());
    }

    /**
     * 计算任务最晚结束时间
     *
     * @param task                  当前任务
     * @param resourceNextStartTime 当前任务所在资源后一个任务开始时间
     * @param tasks                 所有任务
     * @return 当前任务最晚结束时间
     */
    private LocalDateTime calculateLatestEndTime(ProductionTask task, LocalDateTime resourceNextStartTime, List<ProductionTask> tasks) {
        LocalDateTime latestEndTime = resourceNextStartTime;
        if (task.getEndTask() && ObjectNull.isNotNull(task.getOrder()) && ObjectNull.isNotNull(task.getOrder().getDeliveryTime())) {
            latestEndTime = minDateTime(latestEndTime, task.getOrder().getDeliveryTime());
        }
        List<ProductionTask> nextTasks = tasks.stream()
                .filter(t -> ObjectNull.isNotNull(t.getFrontTaskCodes()))
                .filter(t -> t.getFrontTaskCodes().contains(task.getCode()))
                .filter(t -> ObjectNull.isNotNull(t.getStartTime()))
                .toList();
        for (ProductionTask nextTask : nextTasks) {
            LocalDateTime latestEndByNextTask = calculateLatestEndTimeByNextTask(task, nextTask);
            latestEndTime = minDateTime(latestEndTime, latestEndByNextTask);
        }
        return latestEndTime;
    }

    /**
     * 根据后置任务推导当前任务最晚结束时间
     *
     * @param task     当前任务
     * @param nextTask 后置任务
     * @return 当前任务最晚结束时间
     */
    private LocalDateTime calculateLatestEndTimeByNextTask(ProductionTask task, ProductionTask nextTask) {
        if (ObjectNull.isNull(nextTask.getStartTime())) {
            return null;
        }
        Duration preIntervalDuration = DurationUtils.convertDuration(nextTask.getProcess().getPreIntervalDuration());

        // EE关系严格对偶：
        // 1) 当前任务开始时间 <= 后置任务(扣除pre间隔后)开始时间 - buffer
        // 2) 当前任务结束时间 <= 后置任务结束时间 - buffer
        // 结合 end = start + duration，得到:
        // 当前任务最晚结束 = min(后置任务结束-buffer, (后置任务开始-pre-buffer)+当前任务时长)
        if (ProcessRelationshipEnum.EE.equals(nextTask.getProcess().getProcessRelationship())) {
            Duration bufferTime = DurationUtils.convertDuration(nextTask.getProcess().getBufferTime());
            Duration taskDuration = Optional.ofNullable(task.calculateTaskDuration()).orElseGet(() -> Duration.ZERO);
            LocalDateTime latestEndByStartBound = nextTask.getStartTime()
                    .minus(preIntervalDuration)
                    .minus(bufferTime)
                    .plus(taskDuration);
            LocalDateTime latestEndByEndBound = Optional.ofNullable(nextTask.getEndTime())
                    .map(endTime -> endTime.minus(bufferTime))
                    .orElseGet(() -> null);
            return minDateTime(latestEndByStartBound, latestEndByEndBound);
        }

        LocalDateTime latestEndTime = nextTask.getStartTime();
        Duration postIntervalDuration = DurationUtils.convertDuration(task.getProcess().getPostIntervalDuration());
        return latestEndTime.minus(preIntervalDuration).minus(postIntervalDuration);
    }

    /**
     * 取更早的时间
     *
     * @param left  时间1
     * @param right 时间2
     * @return 更早的时间
     */
    private LocalDateTime minDateTime(LocalDateTime left, LocalDateTime right) {
        if (ObjectNull.isNull(left)) {
            return right;
        }
        if (ObjectNull.isNull(right)) {
            return left;
        }
        return left.isBefore(right) ? left : right;
    }
}
