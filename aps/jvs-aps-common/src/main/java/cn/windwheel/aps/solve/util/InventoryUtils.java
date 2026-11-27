package cn.windwheel.aps.solve.util;

import cn.windwheel.aps.solve.model.IncomingMaterialOrder;
import cn.windwheel.aps.solve.model.Material;
import cn.windwheel.aps.solve.model.ProductionOrder;
import cn.windwheel.common.exception.BusinessException;
import cn.windwheel.common.utils.ObjectNull;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * @author jvs
 * 库存工具
 */
public class InventoryUtils {
    private InventoryUtils() {
    }

    /**
     * 在途行的使用明细。quantity 是该行当时的余量，usedQuantity 是本次实际分配量。
     */
    public record LineUse(String lineCode, BigDecimal quantity, BigDecimal usedQuantity,
                          LocalDateTime deliveryTime, LocalDateTime availableAt) {
    }

    /**
     * 一次扣减的过程明细。全部是只读观察值，不参与任何分配决策。
     * onHandAvailable 是轮到本订单时的在库量，与 onHandStart 的差额即此前订单已占用的部分。
     */
    public record Deduction(String orderId, String materialId, String materialCode, BigDecimal requiredQuantity,
                            BigDecimal onHandAvailable, BigDecimal onHandConsumed, BigDecimal inTransitConsumed,
                            BigDecimal lackQuantity, List<LineUse> allocated, List<LineUse> skipped) {
    }

    @FunctionalInterface
    public interface DeductionObserver {
        void observe(Deduction deduction);
    }

    /**
     * 扣减库存
     *
     * @param order                    订单
     * @param material                 物料
     * @param quantity                 物料需求数量
     * @param incomingMaterialOrderMap 按物料id分组的来料订单
     * @return 缺料数量
     */
    public static BigDecimal deductInventory(ProductionOrder order, Material material, BigDecimal quantity,
                                             Map<String, List<IncomingMaterialOrder>> incomingMaterialOrderMap) {
        return deductInventory(order, material, quantity, incomingMaterialOrderMap, at -> {});
    }

    public static BigDecimal deductInventory(ProductionOrder order, Material material, BigDecimal quantity,
            Map<String, List<IncomingMaterialOrder>> incomingMaterialOrderMap,
            Consumer<LocalDateTime> reservation) {
        return deductInventory(order, material, quantity, incomingMaterialOrderMap, reservation, deduction -> {});
    }

    /**
     * 扣减库存，并把扣减过程值交给 observer。分配结果与不传 observer 时完全一致。
     *
     * @param observer 扣减过程观察点，只读
     * @return 缺料数量
     */
    public static BigDecimal deductInventory(ProductionOrder order, Material material, BigDecimal quantity,
            Map<String, List<IncomingMaterialOrder>> incomingMaterialOrderMap,
            Consumer<LocalDateTime> reservation, DeductionObserver observer) {
        if (ObjectNull.isNull(material)) {
            throw new BusinessException("物料不存在", order.getMaterialCode());
        }
        BigDecimal onHandAvailable = material.getQuantity();
        // 优先扣减在库库存
        BigDecimal lackQuantity = deductInStockInventory(material, quantity);
        // 在库库存不够，扣减在途库存（来料订单）
        List<LineUse> allocated = new ArrayList<>();
        List<LineUse> skipped = new ArrayList<>();
        BigDecimal remaining = deductInTransitInventory(lackQuantity, order, material, incomingMaterialOrderMap,
                reservation, allocated, skipped);
        observer.observe(new Deduction(order.getId(), material.getId(), material.getCode(), quantity, onHandAvailable,
                onHandAvailable.subtract(material.getQuantity()), allocated.stream().map(LineUse::usedQuantity)
                        .reduce(BigDecimal.ZERO, BigDecimal::add), remaining, List.copyOf(allocated),
                List.copyOf(skipped)));
        return remaining;
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
     * @param incomingMaterialOrderMap 按物料id分组的在途库存
     * @param allocated                出参：实际参与分配的在途行
     * @param skipped                  出参：因晚于交期被跳过的在途行
     * @return 缺料数量
     */
    private static BigDecimal deductInTransitInventory(BigDecimal requiredQuantity, ProductionOrder order, Material material,
                                                       Map<String, List<IncomingMaterialOrder>> incomingMaterialOrderMap,
                                                       Consumer<LocalDateTime> reservation,
                                                       List<LineUse> allocated, List<LineUse> skipped) {
        // 缺料数量
        BigDecimal lackQuantity = requiredQuantity;
        if (lackQuantity.compareTo(BigDecimal.ZERO) <= 0) {
            return lackQuantity;
        }
        // 扣减库存
        List<IncomingMaterialOrder> incomingMaterialOrders = incomingMaterialOrderMap.get(material.getId());
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
            // 到货加缓冲后的可用时间不得晚于订单交期；恰好在交期可用的来料仍可分配。
            LocalDateTime availableAt = incomingMaterialOrder.getDeliveryTime();
            if (ObjectNull.isNotNull(material.getBufferTimeDuration())) {
                availableAt = availableAt.plus(material.getBufferTimeDuration());
            }
            if (availableAt.isAfter(order.getDeliveryTime())) {
                // 只做记录，不改变跳过行为：这种“有货但到得太晚”的缺口过去不留任何痕迹。
                skipped.add(lineUse(incomingMaterialOrder, availableAt, BigDecimal.ZERO));
                continue;
            }
            // 计算库存
            CalculationInventoryResult result = calculationInventory(incomingMaterialOrder.getQuantity(), lackQuantity);
            reservation.accept(availableAt);
            allocated.add(lineUse(incomingMaterialOrder, availableAt, lackQuantity.subtract(result.getLackQuantity())));
            incomingMaterialOrder.setQuantity(result.getDeductQuantity());
            lackQuantity = result.getLackQuantity();
        }
        return lackQuantity;
    }

    private static LineUse lineUse(IncomingMaterialOrder line, LocalDateTime availableAt, BigDecimal usedQuantity) {
        return new LineUse(line.getCode(), line.getQuantity(), usedQuantity, line.getDeliveryTime(), availableAt);
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
        // 扣减后的库存量
        private BigDecimal deductQuantity;
        // 缺料数量
        private BigDecimal lackQuantity;
    }
}
