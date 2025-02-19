package cn.bctools.aps.solve.util;

import cn.bctools.aps.solve.model.IncomingMaterialOrder;
import cn.bctools.aps.solve.model.Material;
import cn.bctools.aps.solve.model.ProductionOrder;
import cn.bctools.common.exception.BusinessException;
import cn.bctools.common.utils.ObjectNull;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * @author jvs
 * 库存工具
 */
public class InventoryUtils {
    private InventoryUtils() {
    }

    /**
     * 扣减库存
     *
     * @param order                    订单
     * @param material                 物料
     * @param quantity                 物料需求数量
     * @param incomingMaterialOrderMap 来料订单
     * @return 缺料数量
     */
    public static BigDecimal deductInventory(ProductionOrder order, Material material, BigDecimal quantity,
                                             Map<String, List<IncomingMaterialOrder>> incomingMaterialOrderMap) {
        if (ObjectNull.isNull(material)) {
            throw new BusinessException("物料不存在", order.getMaterialCode());
        }
        // 优先扣减在库库存
        BigDecimal lackQuantity = deductInStockInventory(material, quantity);
        // 在库库存不够，扣减在途库存（来料订单）
        return deductInTransitInventory(lackQuantity, order, material, incomingMaterialOrderMap);
    }

    /**
     * 扣减在库库存
     *
     * @param material         物料
     * @param requiredQuantity 需要的库存数量
     * @return 缺料数量
     */
    private static BigDecimal deductInStockInventory(Material material, BigDecimal requiredQuantity) {
        // 扣减库存
        CalculationInventoryResult result = calculationInventory(material.getQuantity(), requiredQuantity);
        material.setQuantity(result.getDeductQuantity());
        // 返回缺料数量
        return result.getLackQuantity();
    }


    /**
     * 扣减在途库存
     *
     * @param requiredQuantity         需要的库存数量
     * @param order                    订单
     * @param material                 物料
     * @param incomingMaterialOrderMap 在途库存
     * @return 缺料数量
     */
    private static BigDecimal deductInTransitInventory(BigDecimal requiredQuantity, ProductionOrder order, Material material,
                                                       Map<String, List<IncomingMaterialOrder>> incomingMaterialOrderMap) {
        // 缺料数量
        BigDecimal lackQuantity = requiredQuantity;
        if (lackQuantity.compareTo(BigDecimal.ZERO) <= 0) {
            return lackQuantity;
        }
        // 扣减库存
        List<IncomingMaterialOrder> incomingMaterialOrders = incomingMaterialOrderMap.get(material.getCode());
        if (ObjectNull.isNull(incomingMaterialOrders)) {
            return lackQuantity;
        }
        incomingMaterialOrders = incomingMaterialOrders.stream()
                .sorted(Comparator.comparing(IncomingMaterialOrder::getDeliveryTime))
                .toList();
        // 获取可用在途库存
        for (IncomingMaterialOrder incomingMaterialOrder : incomingMaterialOrders) {
            // 无缺料，跳过
            if (lackQuantity.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            // 跳过无库存可分配的
            if (incomingMaterialOrder.getQuantity().compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            // 跳过订单交期时间比在途库存预计入库时间晚的
            LocalDateTime deliverTime = incomingMaterialOrder.getDeliveryTime();
            if (ObjectNull.isNotNull(material.getBufferTimeDuration())) {
                deliverTime = deliverTime.plus(material.getBufferTimeDuration());
            }
            if (order.getDeliveryTime().isAfter(deliverTime)) {
                continue;
            }
            // 计算库存
            CalculationInventoryResult result = calculationInventory(incomingMaterialOrder.getQuantity(), lackQuantity);
            incomingMaterialOrder.setQuantity(result.getDeductQuantity());
            lackQuantity = result.getLackQuantity();
        }
        return lackQuantity;
    }

    /**
     * 扣减库存计算
     *
     * @param materialQuantity 物料库存数量
     * @param requiredQuantity 需要的库存数量
     * @return 计算结果
     */
    private static CalculationInventoryResult calculationInventory(BigDecimal materialQuantity, BigDecimal requiredQuantity) {
        // 扣减后的库存量
        BigDecimal deductQuantity = null;
        // 缺料数量
        BigDecimal lackQuantity = BigDecimal.ZERO;
        if (materialQuantity.compareTo(requiredQuantity) >= 0) {
            deductQuantity = materialQuantity.subtract(requiredQuantity);
        } else {
            lackQuantity = requiredQuantity.subtract(materialQuantity);
            deductQuantity = BigDecimal.ZERO;
        }
        CalculationInventoryResult result = new CalculationInventoryResult();
        result.setDeductQuantity(deductQuantity);
        result.setLackQuantity(lackQuantity);
        return result;
    }

    /**
     * 库存计算结果
     */
    @Getter
    @Setter
    static class CalculationInventoryResult {
        // 扣减后的库存数量
        private BigDecimal deductQuantity;
        // 缺料数量
        private BigDecimal lackQuantity;
    }
}
