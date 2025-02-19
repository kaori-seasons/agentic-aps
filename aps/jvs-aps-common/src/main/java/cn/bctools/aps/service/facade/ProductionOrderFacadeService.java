package cn.bctools.aps.service.facade;

import cn.bctools.aps.dto.SaveProductionOrderDTO;
import cn.bctools.aps.dto.UpdateProductionOrderDTO;
import cn.bctools.aps.entity.ProductionOrderPO;

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
