package cn.bctools.aps.service.facade.impl.adjustment.freeze;

import cn.bctools.aps.annotation.AdjustTaskFreeze;
import cn.bctools.aps.dto.schedule.adjustment.FreezeStartedTaskDTO;
import cn.bctools.aps.entity.PlanTaskAdjustPO;
import cn.bctools.aps.entity.PlanTaskPO;
import cn.bctools.aps.entity.enums.PlanTaskStatusEnum;
import cn.bctools.aps.enums.TaskFreezeTypeEnum;
import cn.bctools.common.exception.BusinessException;
import cn.bctools.common.utils.ObjectNull;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * @author jvs
 * 锁定/解锁——所有已开工的任务
 */
@AdjustTaskFreeze(type = TaskFreezeTypeEnum.STARTED_TASKS)
@Service
public class FreezeStartedTaskServiceImpl extends AbstractAdjustTaskFreeze<FreezeStartedTaskDTO> {

    @Override
    protected ToggleTask getToggleFreezeTask(FreezeStartedTaskDTO input) {
        LocalDateTime now = LocalDateTime.now();
        // 优先锁定已调整但未重排的任务，若已调整的任务被锁定，则不同时锁定对应的已排产任务
        // 查询已调整但未重排的任务
        List<PlanTaskAdjustPO> taskAdjustList = planTaskAdjustService.list(Wrappers.<PlanTaskAdjustPO>lambdaQuery()
                .ne(PlanTaskAdjustPO::getTaskStatus, PlanTaskStatusEnum.COMPLETED)
                .le(PlanTaskAdjustPO::getStartTime, now));

        // 查询已排产任务
        List<String> taskAdjustCodes = taskAdjustList.stream().map(PlanTaskAdjustPO::getCode).toList();
        List<PlanTaskPO> planTaskList = planTaskService.list(Wrappers.<PlanTaskPO>lambdaQuery()
                .ne(PlanTaskPO::getTaskStatus, PlanTaskStatusEnum.COMPLETED)
                .le(PlanTaskPO::getStartTime, now));
        // 移除已存在于调整任务中的任务、合并任务的子任务
        planTaskList.removeIf(task -> ObjectNull.isNotNull(task.getMergeTaskCode()) || taskAdjustCodes.contains(task.getCode()));
        // 移除已丢弃的任务、合并任务的子任务
        taskAdjustList.removeIf(task -> ObjectNull.isNotNull(task.getMergeTaskCode()) || task.getDiscard());

        // 校验
        checkCanFreeze(taskAdjustList, planTaskList);

        ToggleTask toggleTask = new ToggleTask();
        toggleTask.setTaskAdjustList(taskAdjustList);
        toggleTask.setPlanTaskList(planTaskList);
        return toggleTask;
    }

    /**
     * 校验是否可锁定/解锁
     *
     * @param taskAdjusts 已调整的任务
     * @param planTasks 已排程的任务
     */
    protected void checkCanFreeze(List<PlanTaskAdjustPO> taskAdjusts, List<PlanTaskPO> planTasks) {
        if (ObjectNull.isNull(taskAdjusts) && ObjectNull.isNull(planTasks)) {
            throw new BusinessException("未找到任务");
        }
    }
}
