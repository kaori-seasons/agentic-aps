package cn.bctools.aps.service.facade.impl.adjustment.merge;

import cn.bctools.aps.annotation.AdjustTaskMerge;
import cn.bctools.aps.dto.schedule.TaskDTO;
import cn.bctools.aps.dto.schedule.adjustment.MergeSelectedTaskDTO;
import cn.bctools.aps.enums.TaskMergeTypeEnum;
import cn.bctools.common.utils.ObjectNull;
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
