package cn.windwheel.aps.service.facade;

import cn.windwheel.aps.dto.schedule.AdjustTaskMoveDTO;
import cn.windwheel.aps.vo.schedule.TaskAdjustMoveVO;

/**
 * @author jvs
 * 排产任务调整 —— 移动任务
 */
public interface AdjustTaskMoveFacadeService<T extends AdjustTaskMoveDTO> {

    /**
     * 移动任务
     *
     * @param input 移动任务入参
     * @return 移动结果
     */
    TaskAdjustMoveVO move(T input);
}
