package cn.windwheel.aps.solve;

import cn.windwheel.aps.solve.model.IncomingMaterialOrder;
import cn.windwheel.aps.solve.model.Material;
import cn.windwheel.aps.solve.model.ProductionOrder;
import cn.windwheel.aps.solve.util.InventoryUtils;
import cn.windwheel.aps.solve.util.InventoryUtils.Deduction;
import cn.windwheel.aps.solve.util.InventoryUtils.LineUse;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 扣减过程值的观察。约束是“只加观察不改行为”：不带观察点的既有结果一个都不能变，
 * 带观察点只是把过程值带出来。既有结果本身由 InventoryUtilsTest 守着，这里只管新增的观察面。
 */
class InventoryObservationTest {
    private static final LocalDateTime DUE = LocalDateTime.of(2026, 11, 20, 18, 0);
    private static final String ID = "material-123";
    private static final String CODE = "RM-STEEL";

    private static Material material(int stock, Duration buffer) {
        return new Material().setId(ID).setCode(CODE).setQuantity(BigDecimal.valueOf(stock)).setBufferTimeDuration(buffer);
    }

    private static IncomingMaterialOrder line(String code, int quantity, LocalDateTime arrival) {
        var line = new IncomingMaterialOrder();
        line.setCode(code);
        line.setMaterialId(ID);
        line.setMaterialCode(CODE);
        line.setQuantity(BigDecimal.valueOf(quantity));
        line.setDeliveryTime(arrival);
        return line;
    }

    private static ProductionOrder order() {
        return new ProductionOrder().setId("order-1").setDeliveryTime(DUE).setMaterialCode("FG");
    }

    private static BigDecimal deduct(Material material, int demand, List<Deduction> observed, IncomingMaterialOrder... lines) {
        return InventoryUtils.deductInventory(order(), material, BigDecimal.valueOf(demand), Map.of(ID, List.of(lines)),
                at -> {}, observed::add);
    }

    private static void same(BigDecimal expected, BigDecimal actual) {
        assertNotNull(actual);
        assertEquals(0, expected.compareTo(actual), () -> "expected " + expected + " actual " + actual);
    }

    private static List<IncomingMaterialOrder> copy(IncomingMaterialOrder[] lines) {
        List<IncomingMaterialOrder> result = new ArrayList<>();
        for (IncomingMaterialOrder line : lines) {
            result.add(line(line.getCode(), line.getQuantity().intValueExact(), line.getDeliveryTime()));
        }
        return result;
    }

    @Test
    void observerDoesNotChangeTheShortageOrTheRemainingQuantities() {
        // 同一份输入跑两遍，一遍带观察点一遍不带：缺料数量、在库余量、每一行在途的余量、预约时刻都必须一致。
        IncomingMaterialOrder[] template = {line("L-early", 4, DUE.minusHours(2)), line("L-late", 20, DUE.plusHours(1)),
                line("L-edge", 6, DUE.minusHours(1))};
        for (int stock : new int[]{0, 3, 10, 12}) {
            for (Duration buffer : new Duration[]{Duration.ZERO, Duration.ofHours(1)}) {
                Material plainMaterial = material(stock, buffer);
                Material observedMaterial = material(stock, buffer);
                List<LocalDateTime> plainReservations = new ArrayList<>();
                List<LocalDateTime> observedReservations = new ArrayList<>();
                List<IncomingMaterialOrder> plainLines = copy(template);
                List<IncomingMaterialOrder> observedLines = copy(template);
                BigDecimal plainLack = InventoryUtils.deductInventory(order(), plainMaterial, BigDecimal.TEN,
                        Map.of(ID, plainLines), plainReservations::add);
                List<Deduction> observed = new ArrayList<>();
                BigDecimal observedLack = InventoryUtils.deductInventory(order(), observedMaterial, BigDecimal.TEN,
                        Map.of(ID, observedLines), observedReservations::add, observed::add);
                String scenario = "stock=" + stock + " buffer=" + buffer;
                assertAll(scenario,
                        () -> same(plainLack, observedLack),
                        () -> same(plainMaterial.getQuantity(), observedMaterial.getQuantity()),
                        () -> assertEquals(plainReservations, observedReservations),
                        () -> assertEquals(1, observed.size()),
                        () -> {
                            for (int i = 0; i < template.length; i++) {
                                same(plainLines.get(i).getQuantity(), observedLines.get(i).getQuantity());
                            }
                        });
                // 观察值还要与不带观察点的那一遍对得上：在库扣减量等于两遍共同的在库落差。
                same(BigDecimal.valueOf(stock).subtract(plainMaterial.getQuantity()), observed.get(0).onHandConsumed());
            }
        }
    }

    @Test
    void lateArrivalIsRecordedAsSkippedInsteadOfVanishing() {
        // 方案最在意的隐形缺料：手上有在途，但到得太晚，系统当它不存在。过去那个 continue 什么都没留下。
        Material material = material(0, Duration.ZERO);
        var late = line("PO-LATE", 10, DUE.plusSeconds(1));
        List<Deduction> observed = new ArrayList<>();
        same(BigDecimal.TEN, deduct(material, 10, observed, late));

        Deduction deduction = observed.get(0);
        same(BigDecimal.TEN, deduction.lackQuantity());
        assertEquals("order-1", deduction.orderId());
        assertEquals(ID, deduction.materialId());
        assertEquals(CODE, deduction.materialCode());
        same(BigDecimal.TEN, deduction.requiredQuantity());
        assertEquals(List.of(), deduction.allocated());
        assertEquals(1, deduction.skipped().size());
        LineUse skipped = deduction.skipped().get(0);
        assertEquals("PO-LATE", skipped.lineCode());
        same(BigDecimal.TEN, skipped.quantity());
        // 被跳过的行没有分出任何数量，在途余量原样保留。
        same(BigDecimal.ZERO, skipped.usedQuantity());
        assertEquals(DUE.plusSeconds(1), skipped.deliveryTime());
        assertEquals(DUE.plusSeconds(1), skipped.availableAt());
        same(BigDecimal.TEN, late.getQuantity());
    }

    @Test
    void bufferIsIncludedInTheRecordedAvailableTime() {
        Material material = material(0, Duration.ofHours(2));
        List<Deduction> observed = new ArrayList<>();
        deduct(material, 10, observed, line("PO-BUFFER", 10, DUE.minusMinutes(30)));
        // 到货 17:30，加 2 小时缓冲后 19:30 可用，晚于 18:00 的交期。
        assertEquals(DUE.plusMinutes(90), observed.get(0).skipped().get(0).availableAt());
        assertEquals(DUE.minusMinutes(30), observed.get(0).skipped().get(0).deliveryTime());
    }

    @Test
    void partialCoverageReportsWhatWasConsumedFromWhere() {
        // 在库 3、按时到 4、晚到 20，需求 10：在库扣 3，在途扣 4，晚到的 20 记为跳过，缺口 3。
        Material material = material(3, Duration.ZERO);
        var early = line("PO-EARLY", 4, DUE.minusHours(2));
        var late = line("PO-LATE", 20, DUE.plusHours(1));
        List<Deduction> observed = new ArrayList<>();
        same(BigDecimal.valueOf(3), deduct(material, 10, observed, late, early));

        Deduction deduction = observed.get(0);
        same(BigDecimal.valueOf(3), deduction.onHandAvailable());
        same(BigDecimal.valueOf(3), deduction.onHandConsumed());
        same(BigDecimal.valueOf(4), deduction.inTransitConsumed());
        same(BigDecimal.valueOf(3), deduction.lackQuantity());
        assertEquals(1, deduction.allocated().size());
        LineUse allocated = deduction.allocated().get(0);
        assertEquals("PO-EARLY", allocated.lineCode());
        same(BigDecimal.valueOf(4), allocated.usedQuantity());
        same(BigDecimal.ZERO, early.getQuantity());
        assertEquals(1, deduction.skipped().size());
        same(BigDecimal.valueOf(20), deduction.skipped().get(0).quantity());
        same(BigDecimal.valueOf(20), late.getQuantity());
    }

    @Test
    void exhaustedLinesAreNotMistakenForLateOnes() {
        // 被别的订单占完的行余量为零，扣减循环会跳过它，但那不是“到得太晚”。
        Material material = material(0, Duration.ZERO);
        List<Deduction> observed = new ArrayList<>();
        deduct(material, 10, observed, line("PO-USED-UP", 0, DUE.plusDays(1)));
        assertEquals(List.of(), observed.get(0).skipped());
        assertEquals(List.of(), observed.get(0).allocated());
    }

    @Test
    void noShortageMeansNoInTransitIsExamined() {
        Material material = material(12, Duration.ZERO);
        List<Deduction> observed = new ArrayList<>();
        same(BigDecimal.ZERO, deduct(material, 10, observed, line("PO-IGNORED", 4, DUE.plusDays(3))));
        Deduction deduction = observed.get(0);
        same(BigDecimal.valueOf(2), material.getQuantity());
        same(BigDecimal.valueOf(12), deduction.onHandAvailable());
        same(BigDecimal.valueOf(10), deduction.onHandConsumed());
        assertEquals(List.of(), deduction.skipped());
        assertEquals(List.of(), deduction.allocated());
    }

    @Test
    void shortageWithoutAnySupplyStillReportsTheOnHandSide() {
        Material material = material(3, Duration.ZERO);
        List<Deduction> observed = new ArrayList<>();
        BigDecimal lack = InventoryUtils.deductInventory(order(), material, BigDecimal.TEN, Map.of(), at -> {}, observed::add);
        same(BigDecimal.valueOf(7), lack);
        Deduction deduction = observed.get(0);
        same(BigDecimal.valueOf(3), deduction.onHandAvailable());
        same(BigDecimal.valueOf(3), deduction.onHandConsumed());
        same(BigDecimal.valueOf(7), deduction.lackQuantity());
        assertEquals(List.of(), deduction.allocated());
        assertEquals(List.of(), deduction.skipped());
    }
}
