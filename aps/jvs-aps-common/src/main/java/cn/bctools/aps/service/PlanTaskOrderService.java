package cn.bctools.aps.service;

import cn.bctools.aps.entity.PlanTaskOrderPO;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.Collection;
import java.util.List;

/**
 * @author jvs
 * 排产计划冗余订单数据
 */
public interface PlanTaskOrderService extends IService<PlanTaskOrderPO> {

    /**
     * 根据订单id集合删除订单
     *
     * @param orderIds 订单id集合
     */
    void removeByOrderIds(Collection<String> orderIds);

    /**
     * 根据订单id查询订单
     *
     * @param orderId 订单id
     * @return 订单
     */
    PlanTaskOrderPO getByOrderId(String orderId);

    /**
     * 根据订单id集合查询订单
     *
     * @param orderIds 订单id集合
     * @return 订单集合
     */
    List<PlanTaskOrderPO> listByOrderIds(Collection<String> orderIds);

}
