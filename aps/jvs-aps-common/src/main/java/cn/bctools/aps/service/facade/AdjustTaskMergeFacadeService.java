package cn.bctools.aps.service.facade;

import cn.bctools.aps.dto.schedule.AdjustTaskMergeDTO;
import cn.bctools.aps.vo.schedule.TaskAdjustMergeVO;

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
