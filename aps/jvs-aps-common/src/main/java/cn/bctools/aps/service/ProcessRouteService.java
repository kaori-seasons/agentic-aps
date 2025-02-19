package cn.bctools.aps.service;

import cn.bctools.aps.entity.ProcessRoutePO;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * @author jvs
 * 工艺路线
 */
public interface ProcessRouteService extends IService<ProcessRoutePO> {

    /**
     * 启用工艺路线
     *
     * @param id      工艺路线id
     */
    void enableProcessRoute(String id);

    /**
     * 禁用工艺路线
     *
     * @param id      工艺路线id
     */
    void disableProcessRoute(String id);
}
