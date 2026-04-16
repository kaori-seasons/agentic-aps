package cn.windwheel.aps.service.facade;

import cn.windwheel.aps.dto.schedule.AdjustTaskMergeDTO;
import cn.windwheel.aps.vo.schedule.TaskAdjustMergeVO;

/**
 * @author jvs
 * 排产任务调整 —— 合并任务
 */
public interface AdjustTaskMergeFacadeService<T extends AdjustTaskMergeDTO> {

    /**
     * 合并任务
     *
     * @param input 合并任务入参
     * @return 合并结果
     */
    TaskAdjustMergeVO merge(T input);
}
