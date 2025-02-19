package cn.bctools.aps.service.facade.impl.adjustment.move;

import cn.bctools.aps.dto.schedule.AdjustTaskMoveDTO;
import cn.bctools.aps.dto.schedule.TaskDTO;
import cn.bctools.aps.service.facade.AdjustTaskMoveFacadeService;
import cn.bctools.aps.service.facade.impl.adjustment.AbstractAdjustTask;
import cn.bctools.aps.vo.schedule.TaskAdjustMoveVO;
import cn.bctools.aps.vo.schedule.report.ResourceGanttTaskVO;
import cn.bctools.common.utils.ObjectNull;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * @author jvs
 * 移动任务模板方法
 */
public abstract class AbstractAdjustTaskMove<T extends AdjustTaskMoveDTO> extends AbstractAdjustTask implements AdjustTaskMoveFacadeService<T> {

    @Override
    public TaskAdjustMoveVO move(T input) {
        // 移动任务
        List<TaskDTO> taskList = moveTask(input);
        if (ObjectNull.isNull(taskList)) {
            return null;
        }

        // 移动任务后，检查是否有任务不合规
        checkTaskCompliance(taskList);

        // 保存任务
        taskAdjustFacadeService.saveAdjustTasks(taskList);

        // 返回将合并任务结果转换为前端展示的数据结构
        return convertTaskAdjustToVo(taskList);
    }

    /**
     * 移动任务
     *
     * @param input 移动任务参数
     * @return 移动后的任务
     */
    protected abstract List<TaskDTO> moveTask(T input);


    /**
     * 重新计算时间后，修改合并任务的子任务的计划时间
     *
     * @param calculateTaskList 计算时间的任务
     */
    protected void updateMergeChildTaskTime(List<TaskDTO> calculateTaskList) {
        Map<String, TaskDTO> planTaskAdjustMap = calculateTaskList.stream()
                .collect(Collectors.toMap(TaskDTO::getCode, Function.identity()));
        List<TaskDTO> taskAdjustChildTasks = taskAdjustFacadeService.listTaskByMergeTaskCodes(planTaskAdjustMap.keySet()).stream()
                .map(task -> {
                    TaskDTO mergeTask = planTaskAdjustMap.get(task.getMergeTaskCode());
                    if (ObjectNull.isNull(mergeTask)) {
                        return null;
                    }
                    task.setStartTime(mergeTask.getStartTime())
                            .setEndTime(mergeTask.getEndTime());
                    return task;
                })
                .filter(ObjectNull::isNotNull)
                .toList();

        calculateTaskList.addAll(taskAdjustChildTasks);
    }


    /**
     * 将结果转换为前端展示的数据结构
     *
     * @param taskList 本次移动重新计算的所有任务
     * @return 转换后的任务
     */
    private TaskAdjustMoveVO convertTaskAdjustToVo(List<TaskDTO> taskList) {
        List<ResourceGanttTaskVO> ganttTaskList = convertResourceGanttTaskList(taskList);
        return new TaskAdjustMoveVO().setRefreshPartialTasks(ganttTaskList);
    }

}
