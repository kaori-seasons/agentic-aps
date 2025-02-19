package cn.bctools.aps.service.facade;

import cn.bctools.aps.dto.schedule.AdjustTaskSplitDTO;
import cn.bctools.aps.vo.schedule.TaskAdjustSplitVO;

/**
 * @author jvs
 * 排产任务调整 —— 拆分排产任务统一接口
 * <p>
 *    所有任务拆分功能都实现此接口
 */
public interface AdjustTaskSplitFacadeService<T extends AdjustTaskSplitDTO> {

    /**
     * 拆分任务
     *
     * @param input 拆分参数
     * @return 拆分结果
     */
    TaskAdjustSplitVO split(T input);

}