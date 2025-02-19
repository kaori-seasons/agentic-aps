package cn.bctools.aps.service.facade.impl.adjustment.freeze;

import cn.bctools.aps.dto.schedule.AdjustTaskFreezeDTO;
import cn.bctools.aps.entity.PlanTaskAdjustPO;
import cn.bctools.aps.entity.PlanTaskPO;
import cn.bctools.aps.service.PlanTaskAdjustService;
import cn.bctools.aps.service.PlanTaskService;
import cn.bctools.aps.service.facade.AdjustTaskFreezeFacadeService;
import cn.bctools.aps.vo.schedule.GanttTaskFreezeVO;
import cn.bctools.aps.vo.schedule.TaskAdjustFreezeVO;
import cn.bctools.common.utils.ObjectNull;
import lombok.Data;

import javax.annotation.Resource;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * @author jvs
 * 任务锁定/解锁模板方法
 */
public abstract class AbstractAdjustTaskFreeze<T extends AdjustTaskFreezeDTO> implements AdjustTaskFreezeFacadeService<T> {

    @Resource
    protected PlanTaskService planTaskService;
    @Resource
    protected PlanTaskAdjustService planTaskAdjustService;

    @Override
    public TaskAdjustFreezeVO freeze(boolean pinned, T input) {
        ToggleTask toggleTask = getToggleFreezeTask(input);
        filterAndProcessTasks(toggleTask);
        if (ObjectNull.isNull(toggleTask)) {
            return null;
        }

        // 锁定/解锁
        toggleFreeze(pinned, toggleTask);

        // 返回将锁定/解锁任务结果转换为前端展示的数据结构
        return convertTaskAdjustToVo(toggleTask);
    }

    /**
     * 获取切换锁定状态的任务
     *
     * @param input 切换锁定状态入参
     * @return 待切换锁定状态的任务
     */
    protected abstract ToggleTask getToggleFreezeTask(T input);

    /**
     * 筛选和处理待锁定的任务
     *
     * @param toggleTask 待锁定的任务
     */
    private void filterAndProcessTasks(ToggleTask toggleTask) {
        if (ObjectNull.isNull(toggleTask)) {
            return;
        }
        // 删除已丢弃的任务
        toggleTask.getTaskAdjustList().removeIf(PlanTaskAdjustPO::getDiscard);

        // 处理合并任务
        // 查询合并任务的子任务
        Set<String> mergeTaskCodes = toggleTask.getTaskAdjustList().stream()
                .filter(PlanTaskAdjustPO::getMergeTask)
                .filter(task -> !task.getDiscard())
                .map(PlanTaskAdjustPO::getCode)
                .collect(Collectors.toSet());
        toggleTask.getPlanTaskList().stream()
                .filter(PlanTaskPO::getMergeTask)
                .map(PlanTaskPO::getCode)
                .forEach(mergeTaskCodes::add);

        // 查询已调整的任务
        List<PlanTaskAdjustPO> childTaskAdjustList = planTaskAdjustService.listTaskByMergeTaskCodes(mergeTaskCodes).stream()
                .filter(task -> !task.getDiscard())
                .collect(Collectors.toList());
        // 查询已排产的任务
        List<PlanTaskPO> childTaskList = planTaskService.listTaskByMergeTaskCodes(mergeTaskCodes);
        List<String> taskAdjustCodeList = childTaskAdjustList.stream()
                .map(PlanTaskAdjustPO::getCode)
                .toList();
        childTaskList.removeIf(task -> taskAdjustCodeList.contains(task.getCode()));
        // 合并任务的子任务加入集合
        if (ObjectNull.isNotNull(childTaskAdjustList)) {
            List<String> childTaskCodes = childTaskAdjustList.stream().map(PlanTaskAdjustPO::getCode).toList();
            toggleTask.getTaskAdjustList().removeIf(task -> childTaskCodes.contains(task.getCode()));
            toggleTask.getTaskAdjustList().addAll(childTaskAdjustList);
        }
        if (ObjectNull.isNotNull(childTaskList)) {
            List<String> childTaskCodes = childTaskList.stream().map(PlanTaskPO::getCode).toList();
            toggleTask.getTaskAdjustList().removeIf(task -> childTaskCodes.contains(task.getCode()));
            toggleTask.getPlanTaskList().addAll(childTaskList);
        }
    }


    /**
     * 切换锁定/解锁
     *
     * @param pinned     true-锁定，false-解锁
     * @param toggleTask 待锁定或解锁的任务
     */
    private void toggleFreeze(Boolean pinned, ToggleTask toggleTask) {
        List<PlanTaskAdjustPO> taskAdjustList = toggleTask.getTaskAdjustList();
        List<PlanTaskPO> planTaskList = toggleTask.getPlanTaskList();
        taskAdjustList.forEach(task -> task.setPinned(pinned));
        planTaskList.forEach(task -> task.setPinned(pinned));

        if (ObjectNull.isNotNull(taskAdjustList)) {
            planTaskAdjustService.updateBatchById(taskAdjustList);
        }
        if (ObjectNull.isNotNull(planTaskList)) {
            planTaskService.updateBatchById(planTaskList);
        }
    }


    /**
     * 将结果转换为前端展示的数据结构
     * <p>
     *     只返回锁定/解锁状态
     *
     * @param toggleTask 本次锁定/解锁的任务
     * @return 转换后的任务
     */
    private TaskAdjustFreezeVO convertTaskAdjustToVo(ToggleTask toggleTask) {
        List<GanttTaskFreezeVO> ganttTaskList = toggleTask.getTaskAdjustList().stream()
                .filter(task -> ObjectNull.isNull(task.getMergeTaskCode()))
                .filter(task -> !task.getDiscard())
                .map(task ->
                        new GanttTaskFreezeVO()
                                .setCode(task.getCode())
                                .setPinned(task.getPinned()))
                .collect(Collectors.toList());

        List<GanttTaskFreezeVO> planTaskGanttList = toggleTask.getPlanTaskList().stream()
                .filter(task -> ObjectNull.isNull(task.getMergeTaskCode()))
                .map(task ->
                        new GanttTaskFreezeVO()
                                .setCode(task.getCode())
                                .setPinned(task.getPinned()))
                .toList();
        ganttTaskList.addAll(planTaskGanttList);

        return new TaskAdjustFreezeVO().setTaskFreezes(ganttTaskList);
    }

    @Data
    protected static class ToggleTask {
        /**
         * 已调整未重排的任务
         */
        private List<PlanTaskAdjustPO> taskAdjustList;

        /**
         * 已排产的任务
         */
        private List<PlanTaskPO> planTaskList;
    }
}
