package cn.bctools.aps.service.facade;

import cn.bctools.aps.dto.schedule.AdjustTaskFreezeDTO;
import cn.bctools.aps.vo.schedule.TaskAdjustFreezeVO;

/**
 * @author jvs
 * 排产任务调整 —— 锁定/解锁排产任务统一接口
 * <p>
 * 所有任务锁定/解锁功能都实现此接口
 */
public interface AdjustTaskFreezeFacadeService<T extends AdjustTaskFreezeDTO> {

    /**
     * 锁定/解锁任务
     *
     * @param pinned true-锁定，false-解锁
     * @param input  锁定/解锁参数
     * @return 锁定/解锁结果
     */
    TaskAdjustFreezeVO freeze(boolean pinned, T input);
}
