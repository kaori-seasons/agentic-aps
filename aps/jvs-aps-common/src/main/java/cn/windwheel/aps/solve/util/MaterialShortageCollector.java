package cn.windwheel.aps.solve.util;

import cn.windwheel.aps.entity.enums.MaterialSourceEnum;
import cn.windwheel.aps.solve.model.Material;
import cn.windwheel.aps.solve.model.ProductionOrder;
import cn.windwheel.aps.solve.util.InventoryUtils.LineUse;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * 收集一次求解内各订单的外购件缺口。
 * <p>
 * 只读 MRP 的过程值，不改变任何扣减结果。订单产品本身的缺口是生产需求，自制子件的缺口由补充订单消化，
 * 两者都不是催料对象，不收；只有非制造来源的子件缺口才进欠料清单，与求解器判定“缺料导致延迟”的口径一致。
 * 同一订单的同一物料可能在 BOM 里出现多次，这里按订单加物料合并成一条。
 */
public class MaterialShortageCollector implements MrpUtils.MrpObserver {

    /**
     * 一个订单在一个物料上的缺口。在途行明细按行号合并，行号缺失时用物料编码加到货时间兜底。
     */
    @Getter
    public static class Gap {
        private final String orderId;
        private final String orderCode;
        private final LocalDateTime deliveryTime;
        private final int sequence;
        private final String materialId;
        private final String materialCode;
        private final String materialName;
        private final String unit;
        private final Duration leadTime;
        private final Duration bufferTime;
        private final BigDecimal onHandStart;
        /**
         * 轮到本订单时的在库量。它与 onHandStart 的差额就是此前订单已占用的部分，是缺口归属的依据。
         */
        private final BigDecimal onHandAvailable;
        private BigDecimal demandQuantity = BigDecimal.ZERO;
        private BigDecimal gapQuantity = BigDecimal.ZERO;
        private BigDecimal onHandConsumed = BigDecimal.ZERO;
        private BigDecimal inTransitConsumed = BigDecimal.ZERO;
        private final Map<String, LineUse> allocated = new TreeMap<>();
        private final Map<String, LineUse> skipped = new TreeMap<>();
        private int occurrences;

        private Gap(ProductionOrder order, int sequence, Material material, BigDecimal onHandStart,
                    BigDecimal onHandAvailable) {
            this.orderId = order.getId();
            this.orderCode = order.getCode();
            this.deliveryTime = order.getDeliveryTime();
            this.sequence = sequence;
            this.materialId = material.getId();
            this.materialCode = material.getCode();
            this.materialName = material.getName();
            this.unit = material.getUnit();
            this.leadTime = material.getLeadTimeDuration();
            this.bufferTime = material.getBufferTimeDuration();
            this.onHandStart = onHandStart;
            this.onHandAvailable = onHandAvailable;
        }

        public BigDecimal getInTransitSkipped() {
            return skipped.values().stream().map(LineUse::quantity).reduce(BigDecimal.ZERO, BigDecimal::add);
        }

        public List<LineUse> getAllocatedLines() {
            return List.copyOf(allocated.values());
        }

        public List<LineUse> getSkippedLines() {
            return List.copyOf(skipped.values());
        }
    }

    private final Map<String, Gap> gaps = new LinkedHashMap<>();
    /**
     * 订单在本轮求解中的扣减位次。缺口归属依赖订单处理顺序，位次要随缺口一起留档。
     */
    private final Map<String, Integer> orderSequence = new HashMap<>();
    /**
     * 物料在本轮求解开始时的在库量。所有扣减都要记，否则自制件和订单产品先占掉的那部分会算不进“此前已占用”。
     */
    private final Map<String, BigDecimal> onHandStart = new HashMap<>();

    @Override
    public void observed(ProductionOrder order, Material material, boolean root, BigDecimal demandQuantity,
                         BigDecimal lackQuantity, InventoryUtils.Deduction deduction) {
        int sequence = orderSequence.computeIfAbsent(order.getId(), k -> orderSequence.size());
        BigDecimal start = onHandStart.putIfAbsent(material.getId(), deduction.onHandAvailable());
        if (root || lackQuantity == null || lackQuantity.signum() <= 0
                || MaterialSourceEnum.PRODUCED.equals(material.getSource())) {
            return;
        }
        String key = order.getId() + "\u0000" + material.getId();
        Gap existing = gaps.get(key);
        Gap gap = existing != null ? existing
                : new Gap(order, sequence, material, start == null ? deduction.onHandAvailable() : start,
                        deduction.onHandAvailable());
        if (existing == null) {
            gaps.put(key, gap);
        }
        gap.occurrences++;
        gap.demandQuantity = gap.demandQuantity.add(demandQuantity);
        gap.gapQuantity = gap.gapQuantity.add(lackQuantity);
        gap.onHandConsumed = gap.onHandConsumed.add(deduction.onHandConsumed());
        deduction.allocated().forEach(line -> merge(gap.allocated, line, true));
        deduction.skipped().forEach(line -> merge(gap.skipped, line, false));
        gap.inTransitConsumed = gap.allocated.values().stream().map(LineUse::usedQuantity)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static void merge(Map<String, LineUse> lines, LineUse line, boolean sumUsed) {
        lines.merge(lineKey(line), line, (a, b) -> new LineUse(a.lineCode(), a.quantity().max(b.quantity()),
                sumUsed ? a.usedQuantity().add(b.usedQuantity()) : BigDecimal.ZERO, a.deliveryTime(), a.availableAt()));
    }

    private static String lineKey(LineUse line) {
        return line.lineCode() != null ? line.lineCode() : line.deliveryTime() + "|" + line.availableAt();
    }

    /**
     * @return 按订单扣减顺序排列的缺口，调用后不可再向采集器追加观测
     */
    public List<Gap> snapshot() {
        return new ArrayList<>(gaps.values());
    }
}
