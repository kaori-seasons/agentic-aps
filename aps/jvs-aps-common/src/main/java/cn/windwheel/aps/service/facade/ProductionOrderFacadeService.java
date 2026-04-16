package cn.windwheel.aps.service.facade;

import cn.windwheel.aps.dto.SaveProductionOrderDTO;
import cn.windwheel.aps.dto.UpdateProductionOrderDTO;
import cn.windwheel.aps.entity.ProductionOrderPO;

/**
 * @author jvs
 * 生产订单聚合服务
 */
public interface ProductionOrderFacadeService {

    /**
     * 新增生产订单
     *
     * @param productionOrder 生产订单
     */
    void save(SaveProductionOrderDTO productionOrder);

    /**
     * 新增生产订单
     *
     * @param productionOrder 生产订单
     */
    void save(ProductionOrderPO productionOrder);

    /**
     * 修改生产订单
     *
     * @param productionOrder 生产订单
     */
    void update(UpdateProductionOrderDTO productionOrder);
}
