package cn.windwheel.aps.service.facade.impl.adjustment.merge;

import cn.windwheel.aps.annotation.AdjustTaskMerge;
import cn.windwheel.aps.dto.schedule.TaskDTO;
import cn.windwheel.aps.dto.schedule.adjustment.MergeSelectedTaskDTO;
import cn.windwheel.aps.enums.TaskMergeTypeEnum;
import cn.windwheel.common.utils.ObjectNull;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * @author jvs
 */
@AdjustTaskMerge(type = TaskMergeTypeEnum.SELECTED_TASKS)
@Service
public class MergeSelectedTaskServiceImpl extends AbstractAdjustTaskMerge<MergeSelectedTaskDTO> {

    @Override
    protected List<TaskDTO> mergeTask(MergeSelectedTaskDTO input) {
        List<TaskDTO> taskAdjusts = taskAdjustFacadeService.listTaskByCodes(input.getTaskCodes()).stream()
                .filter(task -> !task.getDiscard())
                .filter(task -> ObjectNull.isNull(task.getMergeTaskCode()))
                .collect(Collectors.toList());
        // 合并任务
        return executeMergeTasks(taskAdjusts);
    }
}
