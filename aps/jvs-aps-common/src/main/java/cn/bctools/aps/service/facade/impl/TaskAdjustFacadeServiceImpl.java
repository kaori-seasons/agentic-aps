package cn.bctools.aps.service.facade.impl;

import cn.bctools.aps.dto.schedule.TaskDTO;
import cn.bctools.aps.entity.PlanTaskAdjustPO;
import cn.bctools.aps.entity.PlanTaskPO;
import cn.bctools.aps.entity.enums.PlanTaskStatusEnum;
import cn.bctools.aps.service.PlanTaskAdjustService;
import cn.bctools.aps.service.PlanTaskService;
import cn.bctools.aps.service.facade.TaskAdjustFacadeService;
import cn.bctools.common.utils.BeanCopyUtil;
import cn.bctools.common.utils.ObjectNull;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * @author jvs
 */
@Service
public class TaskAdjustFacadeServiceImpl implements TaskAdjustFacadeService {

    @Resource
    private PlanTaskService planTaskService;
    @Resource
    private PlanTaskAdjustService planTaskAdjustService;

    @Override
    public void saveAdjustTasks(List<TaskDTO> taskAdjustList) {
        List<PlanTaskAdjustPO> planTaskAdjustList = BeanCopyUtil.copys(taskAdjustList, PlanTaskAdjustPO.class);
        List<String> subTaskCodeList = planTaskAdjustList.stream().map(PlanTaskAdjustPO::getCode).toList();
        // 先删除再保存
        planTaskAdjustService.remove(Wrappers.<PlanTaskAdjustPO>lambdaQuery().in(PlanTaskAdjustPO::getCode, subTaskCodeList));
        planTaskAdjustService.saveBatch(planTaskAdjustList);
    }

    @Override
    public List<TaskDTO> listAllTasksWithAdjusts() {
        // 查询已排产且未完成的任务
        List<PlanTaskPO> planTaskList = planTaskService.list(Wrappers.<PlanTaskPO>lambdaQuery()
                .ne(PlanTaskPO::getTaskStatus, PlanTaskStatusEnum.COMPLETED));
        // 根据未完成的任务订单，查询已完成的任务
        Set<String> orderIds = planTaskList.stream()
                .map(PlanTaskPO::getProductionOrderId)
                .filter(ObjectNull::isNotNull)
                .collect(Collectors.toSet());
        List<PlanTaskPO> completedTaskList = planTaskService.list(Wrappers.<PlanTaskPO>lambdaQuery()
                .in(PlanTaskPO::getProductionOrderId, orderIds)
                .eq(PlanTaskPO::getTaskStatus, PlanTaskStatusEnum.COMPLETED));
        planTaskList.addAll(completedTaskList);
        // 根据已完成的任务，查询合并任务
        Set<String> mergeTaskCodes = completedTaskList.stream()
                .map(PlanTaskPO::getMergeTaskCode)
                .filter(ObjectNull::isNotNull)
                .collect(Collectors.toSet());
        if (ObjectNull.isNotNull(mergeTaskCodes)) {
            List<String> codes = planTaskList.stream().map(PlanTaskPO::getCode).toList();
            List<PlanTaskPO> mergeTaskList = planTaskService.listByCodes(mergeTaskCodes);
            mergeTaskList.removeIf(task -> codes.contains(task.getCode()));
            if (ObjectNull.isNotNull(mergeTaskList)) {
                planTaskList.addAll(mergeTaskList);
            }
        }

        // 查询已调整的任务
        List<PlanTaskAdjustPO> planTaskAdjustList = planTaskAdjustService.list().stream()
                .filter(task -> !task.getDiscard())
                .collect(Collectors.toList());

        // 合并任务
        return mergeTasksIntoAdjustedTasks(planTaskAdjustList, planTaskList);
    }

    @Override
    public List<TaskDTO> listPartiallyCompletedTask() {
        // 查询已调整的任务
        List<PlanTaskAdjustPO> planTaskAdjustList = planTaskAdjustService.list(Wrappers.<PlanTaskAdjustPO>lambdaQuery()
                .eq(PlanTaskAdjustPO::getTaskStatus, PlanTaskStatusEnum.PARTIALLY_COMPLETED));

        // 查询已排产的任务
        List<PlanTaskPO> planTaskList = planTaskService.list(Wrappers.<PlanTaskPO>lambdaQuery()
                .in(PlanTaskPO::getTaskStatus, PlanTaskStatusEnum.PARTIALLY_COMPLETED));

        // 合并任务
        return mergeTasksIntoAdjustedTasks(planTaskAdjustList, planTaskList);
    }

    @Override
    public TaskDTO getTaskByCode(String taskCode) {
        if (ObjectNull.isNull(taskCode)) {
            return null;
        }
        // 优先查询已调整的任务
        PlanTaskAdjustPO taskAdjust = planTaskAdjustService.getByCode(taskCode);
        if (ObjectNull.isNotNull(taskAdjust)) {
            return BeanCopyUtil.copy(taskAdjust, TaskDTO.class).setAdjusted(true);
        }
        return BeanCopyUtil.copy(planTaskService.getByCode(taskCode), TaskDTO.class).setDiscard(false).setCompliant(true);
    }

    @Override
    public List<TaskDTO> listTaskByCodes(Collection<String> taskCodes) {
        if (ObjectNull.isNull(taskCodes)) {
            return Collections.emptyList();
        }
        // 查询已调整的任务
        List<PlanTaskAdjustPO> planTaskAdjustList = planTaskAdjustService.list(Wrappers.<PlanTaskAdjustPO>lambdaQuery()
                .in(PlanTaskAdjustPO::getCode, taskCodes));

        // 查询已排产的任务
        List<PlanTaskPO> planTaskList = planTaskService.list(Wrappers.<PlanTaskPO>lambdaQuery()
                .in(PlanTaskPO::getCode, taskCodes));

        // 合并任务
        return mergeTasksIntoAdjustedTasks(planTaskAdjustList, planTaskList);
    }

    @Override
    public List<TaskDTO> listTasksWithAdjustsByResourceId(String resourceId, LocalDateTime time) {
        // 查询已调整的任务
        List<PlanTaskAdjustPO> planTaskAdjustList = planTaskAdjustService.list(Wrappers.<PlanTaskAdjustPO>lambdaQuery()
                .eq(PlanTaskAdjustPO::getMainResourceId, resourceId)
                .ge(PlanTaskAdjustPO::getStartTime, time));

        // 查询已排产的任务
        List<PlanTaskPO> planTaskList = planTaskService.list(Wrappers.<PlanTaskPO>lambdaQuery()
                .eq(PlanTaskPO::getMainResourceId, resourceId)
                .ge(PlanTaskPO::getStartTime, time));

        // 合并任务
        return mergeTasksIntoAdjustedTasks(planTaskAdjustList, planTaskList);
    }

    @Override
    public List<TaskDTO> listTasksWithAdjustsByResourceIds(Collection<String> resourceIds) {
        // 查询已调整的任务
        List<PlanTaskAdjustPO> planTaskAdjustList = planTaskAdjustService.list(Wrappers.<PlanTaskAdjustPO>lambdaQuery()
                .in(PlanTaskAdjustPO::getMainResourceId, resourceIds)
                .ne(PlanTaskAdjustPO::getTaskStatus, PlanTaskStatusEnum.COMPLETED));

        // 查询已排产的任务
        List<PlanTaskPO> planTaskList = planTaskService.list(Wrappers.<PlanTaskPO>lambdaQuery()
                .in(PlanTaskPO::getMainResourceId, resourceIds)
                .ne(PlanTaskPO::getTaskStatus, PlanTaskStatusEnum.COMPLETED));

        // 合并任务
        return mergeTasksIntoAdjustedTasks(planTaskAdjustList, planTaskList);
    }

    @Override
    public List<TaskDTO> listTaskByMergeTaskCode(String mergeTaskCode) {
        return listTaskByMergeTaskCodes(Collections.singletonList(mergeTaskCode));
    }

    @Override
    public List<TaskDTO> listTaskByMergeTaskCodes(Collection<String> mergeTaskCodes) {
        if (ObjectNull.isNull(mergeTaskCodes)) {
            return Collections.emptyList();
        }
        // 查询已调整的任务
        List<PlanTaskAdjustPO> planTaskAdjustList = planTaskAdjustService.list(Wrappers.<PlanTaskAdjustPO>lambdaQuery()
                .in(PlanTaskAdjustPO::getMergeTaskCode, mergeTaskCodes));

        // 查询已排产的任务
        List<PlanTaskPO> planTaskList = planTaskService.list(Wrappers.<PlanTaskPO>lambdaQuery()
                .in(PlanTaskPO::getMergeTaskCode, mergeTaskCodes));

        // 合并任务
        return mergeTasksIntoAdjustedTasks(planTaskAdjustList, planTaskList);
    }

    @Override
    public List<TaskDTO> listTasksByDateRange(LocalDateTime beginDate, LocalDateTime endDate) {
        List<PlanTaskAdjustPO> planTaskAdjustList = planTaskAdjustService.list(Wrappers.<PlanTaskAdjustPO>lambdaQuery()
                .le(ObjectNull.isNotNull(endDate), PlanTaskAdjustPO::getStartTime, endDate)
                .ge(PlanTaskAdjustPO::getEndTime, beginDate));

        // 查询已排产的任务
        List<PlanTaskPO> planTaskList = planTaskService.list(Wrappers.<PlanTaskPO>lambdaQuery()
                .le(ObjectNull.isNotNull(endDate), PlanTaskPO::getStartTime, endDate)
                .ge(PlanTaskPO::getEndTime, beginDate));

        // 合并任务
        return mergeTasksIntoAdjustedTasks(planTaskAdjustList, planTaskList);
    }

    @Override
    public LocalDateTime getEarliestTaskStartTime() {
        LocalDateTime taskEarliestTaskStartTime = planTaskService.getEarliestTaskStartTime();
        LocalDateTime taskAdjustEarliestTaskStartTime = planTaskAdjustService.getEarliestTaskStartTime();
        if (ObjectNull.isNull(taskEarliestTaskStartTime) && ObjectNull.isNull(taskAdjustEarliestTaskStartTime)) {
            return null;
        }
        if (ObjectNull.isNull(taskEarliestTaskStartTime) && ObjectNull.isNotNull(taskAdjustEarliestTaskStartTime)) {
            return taskAdjustEarliestTaskStartTime;
        }
        if (ObjectNull.isNotNull(taskEarliestTaskStartTime) && ObjectNull.isNull(taskAdjustEarliestTaskStartTime)) {
            return taskEarliestTaskStartTime;
        }
        if (taskEarliestTaskStartTime.isBefore(taskAdjustEarliestTaskStartTime) ) {
            return taskEarliestTaskStartTime;
        } else {
            return taskAdjustEarliestTaskStartTime;
        }
    }

    /**
     * 合并任务到已调整任务集合
     *
     * @param planTaskAdjustList 已调整任务集合
     * @param planTaskList       任务集合
     */
    private List<TaskDTO> mergeTasksIntoAdjustedTasks(List<PlanTaskAdjustPO> planTaskAdjustList, List<PlanTaskPO> planTaskList) {
        // 以已调整的任务优先（从已排产任务集合中排除已调整的任务）
        removeExistsAdjustTask(planTaskAdjustList, planTaskList);

        // 合并任务
        List<TaskDTO> taskList = new ArrayList<>();
        if (ObjectNull.isNotNull(planTaskAdjustList)) {
            planTaskAdjustList.forEach(task -> {
                TaskDTO taskAdjust = BeanCopyUtil.copy(task, TaskDTO.class)
                                .setAdjusted(true);
                taskList.add(taskAdjust);
            });
        }
        if (ObjectNull.isNotNull(planTaskList)) {
            planTaskList.forEach(task -> {
                TaskDTO planTask = BeanCopyUtil.copy(task, TaskDTO.class)
                        .setDiscard(false)
                        .setCompliant(true)
                        .setAdjusted(false);
                taskList.add(planTask);
            });
        }
        return taskList;
    }

    /**
     * 从已排产任务集合中排除已调整的任务
     *
     * @param planTaskAdjustList 已调整的任务集合
     * @param planTaskList       排程任务集合
     */
    private void removeExistsAdjustTask(List<PlanTaskAdjustPO> planTaskAdjustList, List<PlanTaskPO> planTaskList) {
        List<String> taskAdjustCodeList = planTaskAdjustList.stream()
                .map(PlanTaskAdjustPO::getCode)
                .toList();
        planTaskList.removeIf(task -> taskAdjustCodeList.contains(task.getCode()));
    }
}
