package cn.windwheel.aps.service.facade.impl.adjustment.split;

import cn.windwheel.aps.annotation.AdjustTaskSplit;
import cn.windwheel.aps.dto.schedule.TaskDTO;
import cn.windwheel.aps.dto.schedule.adjustment.SplitEvenlyByTaskNumberDTO;
import cn.windwheel.aps.enums.TaskSplitTypeEnum;
import cn.windwheel.aps.util.BigDecimalUtils;
import cn.windwheel.common.exception.BusinessException;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * @author jvs
 * 按任务数量平均拆分
 */
@AdjustTaskSplit(type = TaskSplitTypeEnum.SPLIT_EVENLY_BY_TASK_NUMBER)
@Service
public class SplitEvenlyByTaskNumberServiceImpl extends AbstractAdjustTaskSplit<SplitEvenlyByTaskNumberDTO> {

    @Override
    public List<TaskDTO> getSubTasks(SplitEvenlyByTaskNumberDTO taskAdjust) {
        Integer splitTaskNumber = taskAdjust.getSplitTaskNumber();
        // 查询待拆分任务。 优先查询已调整的任务
        TaskDTO splitTask = taskAdjustFacadeService.getTaskByCode(taskAdjust.getTaskCode());
        // 校验是否可拆分
        checkCanSplit(splitTask);
        BigDecimal scheduledQuantity = BigDecimalUtils.stripTrailingZeros(splitTask.getScheduledQuantity());
        if (splitTaskNumber > scheduledQuantity.intValue()) {
            throw new BusinessException("拆分任务数量不能大于任务计划生产数量");
        }

        // 各子任务计划生产数量
        BigDecimal splitNumber = new BigDecimal(splitTaskNumber);
        BigDecimal average = scheduledQuantity.divide(splitNumber, RoundingMode.DOWN);
        List<BigDecimal> subScheduledQuantityList = IntStream.range(0, splitTaskNumber)
                .mapToObj(i -> average)
                .collect(Collectors.toList());
        // 计算按平均值分配后的余数，并将余数分配到前几个子任务
        BigDecimal remainder = scheduledQuantity.subtract(average.multiply(splitNumber));
        for (int i = 0; i < remainder.intValue(); i++) {
            subScheduledQuantityList.set(i, subScheduledQuantityList.get(i).add(BigDecimal.ONE));
        }

        // 拆分任务
        return splitTask(splitTask, subScheduledQuantityList);
    }
}
