package cn.bctools.aps.service.facade.impl.adjustment.move;

import cn.bctools.aps.annotation.AdjustTaskMove;
import cn.bctools.aps.dto.schedule.TaskDTO;
import cn.bctools.aps.dto.schedule.adjustment.MovePositionDTO;
import cn.bctools.aps.entity.enums.PlanTaskStatusEnum;
import cn.bctools.aps.enums.TaskMoveTypeEnum;
import cn.bctools.aps.solve.model.WorkCalendar;
import cn.bctools.aps.solve.util.TaskValidatorUtils;
import cn.bctools.common.exception.BusinessException;
import cn.bctools.common.utils.ObjectNull;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * @author jvs
 * 移动单个任务
 */
@AdjustTaskMove(type = TaskMoveTypeEnum.MOVE_POSITION)
@Service
@AllArgsConstructor
public class MovePositionServiceImpl extends AbstractAdjustTaskMove<MovePositionDTO> {


    @Override
    protected List<TaskDTO> moveTask(MovePositionDTO movePosition) {
        TaskDTO taskAdjust = taskAdjustFacadeService.getTaskByCode(movePosition.getTaskCode());
        if (ObjectNull.isNotNull(taskAdjust.getMergeTaskCode())) {
            throw new BusinessException("不能移动合并任务的子任务");
        }
        if (taskAdjust.getDiscard()) {
            throw new BusinessException("不能移动已丢弃的任务");
        }
        if (taskAdjust.getPinned()) {
            throw new BusinessException("不能移动已锁定的任务");
        }
        if (PlanTaskStatusEnum.COMPLETED.equals(taskAdjust.getTaskStatus())) {
            throw new BusinessException("不能移动已完成的任务");
        }
        // 任务只能移动到工序指定可用的设备上
        boolean matchResource = TaskValidatorUtils.matchTaskResource(movePosition.getResourceId(), taskAdjust.getProcessInfo());
        if (!matchResource) {
            throw new BusinessException("不能移动到该资源");
        }
        // 任务不能移动到已完成或部分完成的任务之前
        List<TaskDTO> resourceTaskAdjustList = taskAdjustFacadeService.listTasksWithAdjustsByResourceId(movePosition.getResourceId(), movePosition.getNewStartTime())
                .stream()
                .filter(task -> !task.getDiscard())
                .collect(Collectors.toList());
        boolean hasNoPendingTask = resourceTaskAdjustList.stream().anyMatch(task -> !PlanTaskStatusEnum.PENDING.equals(task.getTaskStatus()));
        if (hasNoPendingTask) {
            throw new BusinessException("不能移动到已完成或部分完成的任务之前");
        }

        resourceTaskAdjustList.removeIf(task -> task.getCode().equals(taskAdjust.getCode()));

        // 参与时间计算的任务
        List<TaskDTO> calculateTaskList = resourceTaskAdjustList.stream()
                .filter(task -> ObjectNull.isNull(task.getMergeTaskCode()))
                .filter(task -> !task.getDiscard())
                .collect(Collectors.toList());
        // 获取所有待修改时间的前置任务
        List<TaskDTO> frontTasks = listAdjustTaskFrontTasks(resourceTaskAdjustList);

        // 将被移动的任务加入待计算集合
        taskAdjust
                .setStartTime(movePosition.getNewStartTime())
                .setMainResourceId(movePosition.getResourceId());
        calculateTaskList.add(taskAdjust);

        // 移动后重新计算任务时间
        Comparator<TaskDTO> comparing = Comparator.comparing(task -> !task.getCode().equals(taskAdjust.getCode()));
        comparing = comparing.thenComparing(TaskDTO::getStartTime);
        calculateTaskList.sort(comparing);

        // 计算开始时间
        Set<String> resourceIds = frontTasks.stream().map(TaskDTO::getMainResourceId).collect(Collectors.toSet());
        resourceIds.add(taskAdjust.getMainResourceId());
        Map<String, List<WorkCalendar>> workCalendarMap = workCalendarFacadeService.listResourceScheduleCalendar(resourceIds);
        calculateTaskTime(calculateTaskList, frontTasks, workCalendarMap);

        // 重新计算时间后，修改合并任务的子任务的计划时间
        updateMergeChildTaskTime(calculateTaskList);

        return calculateTaskList;
    }
}
