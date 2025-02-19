package cn.bctools.aps.service.facade.impl.adjustment.freeze;

import cn.bctools.aps.annotation.AdjustTaskFreeze;
import cn.bctools.aps.dto.schedule.adjustment.FreezeSelectedTaskDTO;
import cn.bctools.aps.entity.PlanTaskAdjustPO;
import cn.bctools.aps.entity.PlanTaskPO;
import cn.bctools.aps.enums.TaskFreezeTypeEnum;
import cn.bctools.common.exception.BusinessException;
import cn.bctools.common.utils.ObjectNull;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * @author jvs
 * 锁定/解锁——选中的任务
 */
@AdjustTaskFreeze(type = TaskFreezeTypeEnum.SELECTED_TASKS)
@Service
public class FreezeSelectedTaskServiceImpl extends AbstractAdjustTaskFreeze<FreezeSelectedTaskDTO> {

    @Override
    protected ToggleTask getToggleFreezeTask(FreezeSelectedTaskDTO input) {
        Set<String> taskCodes = input.getTaskCodes();
        // 优先锁定已调整但未重排的任务，若已调整的任务被锁定，则不同时锁定对应的已排产任务
        // 查询已调整但未重排的任务
        List<PlanTaskAdjustPO> taskAdjustList = planTaskAdjustService.listByCodes(taskCodes);
        List<String> taskAdjustCodes = taskAdjustList.stream().map(PlanTaskAdjustPO::getCode).toList();

        // 查询已排产任务
        taskCodes.removeIf(taskAdjustCodes::contains);
        List<PlanTaskPO> planTaskList = planTaskService.listByCodes(taskCodes);

        // 校验是否可锁定/解锁
        checkCanFreeze(input.getPinned(), taskAdjustList, planTaskList);

        ToggleTask toggleTask = new ToggleTask();
        toggleTask.setTaskAdjustList(ObjectNull.isNotNull(taskAdjustList) ? taskAdjustList : new ArrayList<>());
        toggleTask.setPlanTaskList(ObjectNull.isNotNull(planTaskList) ? planTaskList : new ArrayList<>());
        return toggleTask;
    }

    /**
     * 校验是否可锁定/解锁
     *
     * @param pinned true-锁定，false-解锁
     * @param taskAdjusts 已调整的任务
     * @param planTasks 已排程的任务
     */
    protected void checkCanFreeze(Boolean pinned, List<PlanTaskAdjustPO> taskAdjusts, List<PlanTaskPO> planTasks) {
        boolean existsMergeChildAdjustTask = taskAdjusts.stream().anyMatch(task -> ObjectNull.isNotNull(task.getMergeTaskCode()));
        boolean existsMergeChildTask = planTasks.stream().anyMatch(task -> ObjectNull.isNotNull(task.getMergeTaskCode()));
        if (existsMergeChildAdjustTask || existsMergeChildTask) {
            throw new BusinessException("不能锁定或解锁合并任务的子任务");
        }

        boolean existsDiscard = taskAdjusts.stream().anyMatch(PlanTaskAdjustPO::getDiscard);
        if (existsDiscard) {
            throw new BusinessException("不能锁定或解锁已丢弃的任务");
        }
    }
}
