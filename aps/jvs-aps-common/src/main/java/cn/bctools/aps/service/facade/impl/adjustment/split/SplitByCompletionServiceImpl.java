package cn.bctools.aps.service.facade.impl.adjustment.split;

import cn.bctools.aps.annotation.AdjustTaskSplit;
import cn.bctools.aps.dto.schedule.TaskDTO;
import cn.bctools.aps.dto.schedule.adjustment.SplitByCompletionDTO;
import cn.bctools.aps.entity.enums.PlanTaskStatusEnum;
import cn.bctools.aps.enums.TaskSplitTypeEnum;
import cn.bctools.common.exception.BusinessException;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * @author jvs
 * 按完成数量拆分
 */
@AdjustTaskSplit(type = TaskSplitTypeEnum.SPLIT_BY_COMPLETION)
@Service
public class SplitByCompletionServiceImpl extends AbstractAdjustTaskSplit<SplitByCompletionDTO> {

    @Override
    public List<TaskDTO> getSubTasks(SplitByCompletionDTO taskAdjust) {
        // 查询待拆分任务。 优先查询已调整的任务
        TaskDTO splitTask = taskAdjustFacadeService.getTaskByCode(taskAdjust.getTaskCode());
        // 校验是否可拆分
        checkCanSplit(splitTask);
        if (!PlanTaskStatusEnum.PARTIALLY_COMPLETED.equals(splitTask.getTaskStatus())) {
            throw new BusinessException("没有已完成数量不能拆分");
        }

        // 各子任务计划生产数量
        List<BigDecimal> subScheduledQuantityList = new ArrayList<>();
        subScheduledQuantityList.add(splitTask.getQuantityCompleted());
        subScheduledQuantityList.add(splitTask.getScheduledQuantity().subtract(splitTask.getQuantityCompleted()));

        // 拆分任务
        return splitTask(splitTask, subScheduledQuantityList);
    }
}
