package cn.windwheel.aps.service.facade.impl.adjustment.split;

import cn.windwheel.aps.annotation.AdjustTaskSplit;
import cn.windwheel.aps.dto.schedule.TaskDTO;
import cn.windwheel.aps.dto.schedule.adjustment.SplitByQuantityDTO;
import cn.windwheel.aps.enums.TaskSplitTypeEnum;
import cn.windwheel.common.exception.BusinessException;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * @author jvs
 * 按拆出数量拆分
 */
@AdjustTaskSplit(type = TaskSplitTypeEnum.SPLIT_BY_QUANTITY)
@Service
public class SplitByQuantityServiceImpl extends AbstractAdjustTaskSplit<SplitByQuantityDTO> {

    @Override
    public List<TaskDTO> getSubTasks(SplitByQuantityDTO taskAdjust) {
        BigDecimal splitQuantity = taskAdjust.getSplitQuantity();

        // 查询待拆分任务。 优先查询已调整的任务
        TaskDTO splitTask = taskAdjustFacadeService.getTaskByCode(taskAdjust.getTaskCode());
        // 校验是否可拆分
        checkCanSplit(splitTask);
        if (splitQuantity.compareTo(splitTask.getScheduledQuantity()) >= 0) {
            throw new BusinessException("拆出数量不能大于或等于任务计划生产数量");
        }

        // 各子任务计划生产数量
        List<BigDecimal> subScheduledQuantityList = new ArrayList<>();
        subScheduledQuantityList.add(splitTask.getScheduledQuantity().subtract(splitQuantity));
        subScheduledQuantityList.add(splitQuantity);

        // 拆分任务
        return splitTask(splitTask, subScheduledQuantityList);
    }
}
