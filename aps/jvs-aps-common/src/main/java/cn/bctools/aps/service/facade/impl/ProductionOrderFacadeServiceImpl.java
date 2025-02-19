package cn.bctools.aps.service.facade.impl;

import cn.bctools.aps.dto.SaveProductionOrderDTO;
import cn.bctools.aps.dto.UpdateProductionOrderDTO;
import cn.bctools.aps.entity.ProductionOrderPO;
import cn.bctools.aps.entity.enums.OrderSchedulingStatusEnum;
import cn.bctools.aps.entity.enums.OrderStatusEnum;
import cn.bctools.aps.service.MaterialService;
import cn.bctools.aps.service.ProductionOrderService;
import cn.bctools.aps.service.facade.ProductionOrderFacadeService;
import cn.bctools.common.exception.BusinessException;
import cn.bctools.common.utils.BeanCopyUtil;
import cn.bctools.common.utils.ObjectNull;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * @author jvs
 */
@Service
@AllArgsConstructor
public class ProductionOrderFacadeServiceImpl implements ProductionOrderFacadeService {
    private final MaterialService materialService;
    private final ProductionOrderService productionOrderService;
    public static final String DEFAULT_COLOR = "#99CCFF";

    @Transactional(rollbackFor = Exception.class)
    @Override
    public void save(SaveProductionOrderDTO saveProductionOrder) {
        ProductionOrderPO productionOrder = BeanCopyUtil.copy(saveProductionOrder, ProductionOrderPO.class);
        save(productionOrder);
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public void save(ProductionOrderPO productionOrder) {
        checkCanSave(productionOrder);
        setDefault(productionOrder);
        productionOrderService.save(productionOrder);
    }

    @Override
    public void update(UpdateProductionOrderDTO updateProductionOrder) {
        ProductionOrderPO existsOrder = productionOrderService.getById(updateProductionOrder.getId());
        if (ObjectNull.isNull(existsOrder)) {
            throw new BusinessException("生产订单不存在");
        }
        ProductionOrderPO updateOrderInfo = BeanCopyUtil.copy(updateProductionOrder, ProductionOrderPO.class);
        checkCanSave(updateOrderInfo);
        updateOrderInfo.setSchedulingStatus(existsOrder.getSchedulingStatus());
        setDefault(updateOrderInfo);
        // 修改订单
        productionOrderService.updateById(updateOrderInfo);
    }


    /**
     * 设置默认值
     *
     * @param productionOrder 生产订单
     */
    private void setDefault(ProductionOrderPO productionOrder) {
        productionOrder.setSupplement(false);
        // 设置默认值
        if (ObjectNull.isNull(productionOrder.getPriority())) {
            productionOrder.setPriority(1);
        }
        if (ObjectNull.isNull(productionOrder.getOrderStatus())) {
            productionOrder.setOrderStatus(OrderStatusEnum.PENDING);
        }
        if (ObjectNull.isNull(productionOrder.getSchedulingStatus())) {
            productionOrder.setSchedulingStatus(OrderSchedulingStatusEnum.UNSCHEDULED);
        }
        if (ObjectNull.isNull(productionOrder.getCanSchedule())) {
            productionOrder.setCanSchedule(true);
        }
        if (!OrderStatusEnum.PENDING.equals(productionOrder.getOrderStatus())) {
            // 若订单状态不是待处理，则设置不参与排程
            productionOrder.setCanSchedule(false);
        }
        if (ObjectNull.isNull(productionOrder.getColor())) {
            productionOrder.setColor(DEFAULT_COLOR);
        }
        // 新增数据设置默认值
        if (ObjectNull.isNull(productionOrder.getId())) {
            // 序号+1
            BigDecimal sequenceMax = Optional.ofNullable(productionOrderService.getOne(Wrappers.<ProductionOrderPO>lambdaQuery()
                            .orderByDesc(ProductionOrderPO::getSequence)
                            .last("limit 1")))
                    .map(ProductionOrderPO::getSequence)
                    .orElseGet(() -> BigDecimal.ZERO);
            productionOrder.setSequence(sequenceMax.add(BigDecimal.ONE));
        }
    }

    /**
     * 检查是否可保存
     *
     * @param productionOrder 生产订单
     */
    private void checkCanSave(ProductionOrderPO productionOrder) {
        // 校验生产订单号是否重复
        checkExistsProductionOrder(productionOrder.getId(), productionOrder.getCode());
        // 校验物料编码是否存在
        checkExistsMaterialCode(productionOrder.getMaterialCode());
    }

    /**
     * 校验生产订单号是否重复
     *
     * @param id   生产订单id
     * @param code 生产订单号
     */
    private void checkExistsProductionOrder(String id, String code) {
        ProductionOrderPO existsProductionOrder = productionOrderService.getOne(Wrappers.<ProductionOrderPO>lambdaQuery()
                .select(ProductionOrderPO::getId)
                .eq(ProductionOrderPO::getCode, code));
        if (ObjectNull.isNull(existsProductionOrder)) {
            return;
        }
        if (existsProductionOrder.getId().equals(id)) {
            return;
        }
        throw new BusinessException("生产订单号已存在");
    }

    /**
     * 校验物料编码是否存在
     *
     * @param materialCode 物料编码
     */
    private void checkExistsMaterialCode(String materialCode) {
        if (materialService.existsCode(materialCode)) {
            return;
        }
        throw new BusinessException("物料编码不存在");
    }

}
