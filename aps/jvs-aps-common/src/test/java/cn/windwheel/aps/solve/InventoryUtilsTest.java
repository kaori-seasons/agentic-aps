package cn.windwheel.aps.solve;

import cn.windwheel.aps.solve.model.IncomingMaterialOrder;
import cn.windwheel.aps.solve.model.Material;
import cn.windwheel.aps.solve.model.ProductionOrder;
import cn.windwheel.aps.solve.util.InventoryUtils;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class InventoryUtilsTest {
    private static final LocalDateTime DUE = LocalDateTime.of(2026, 11, 20, 18, 0);
    private static final String ID = "material-123";
    private static final String CODE = "RM-STEEL";

    private static Material material(int stock) {
        return new Material().setId(ID).setCode(CODE).setQuantity(BigDecimal.valueOf(stock));
    }

    private static IncomingMaterialOrder shipment(int quantity, LocalDateTime arrival) {
        var shipment = new IncomingMaterialOrder();
        shipment.setMaterialId(ID);
        shipment.setMaterialCode(CODE);
        shipment.setQuantity(BigDecimal.valueOf(quantity));
        shipment.setDeliveryTime(arrival);
        return shipment;
    }

    private static BigDecimal deduct(Material material, int demand, IncomingMaterialOrder... shipments) {
        var order = new ProductionOrder().setDeliveryTime(DUE).setMaterialCode(CODE);
        // BasicDataComponent groups incoming shipments by material ID, which differs from the code.
        return InventoryUtils.deductInventory(order, material, BigDecimal.valueOf(demand),
                Map.of(ID, List.of(shipments)));
    }

    private static void quantity(int expected, BigDecimal actual) {
        assertEquals(0, BigDecimal.valueOf(expected).compareTo(actual),
                () -> "Expected quantity " + expected + ", actual " + actual);
    }

    @Test
    void usesEarlyArrivalWhenMaterialIdDiffersFromCode() {
        var early = shipment(10, DUE.minusDays(1));
        quantity(0, deduct(material(0), 10, early));
        quantity(0, early.getQuantity());
    }

    @Test
    void acceptsArrivalExactlyAtTheDeadline() {
        var onTime = shipment(10, DUE);
        quantity(0, deduct(material(0), 10, onTime));
        quantity(0, onTime.getQuantity());
    }

    @Test
    void leavesLateArrivalUnallocated() {
        var late = shipment(10, DUE.plusSeconds(1));
        quantity(10, deduct(material(0), 10, late));
        quantity(10, late.getQuantity());
    }

    @Test
    void bufferCanMakeAnEarlyShipmentUnavailable() {
        var material = material(0).setBufferTimeDuration(Duration.ofHours(1));
        var shipment = shipment(10, DUE.minusMinutes(30));
        quantity(10, deduct(material, 10, shipment));
        quantity(10, shipment.getQuantity());
    }

    @Test
    void acceptsArrivalWhenBufferEndsExactlyAtTheDeadline() {
        var material = material(0).setBufferTimeDuration(Duration.ofHours(1));
        var shipment = shipment(10, DUE.minusHours(1));
        quantity(0, deduct(material, 10, shipment));
        quantity(0, shipment.getQuantity());
    }

    @Test
    void partialArrivalLeavesOnlyTheUncoveredDemandAndPreservesLateStock() {
        var material = material(3);
        var early = shipment(4, DUE.minusHours(2));
        var late = shipment(20, DUE.plusHours(1));
        quantity(3, deduct(material, 10, late, early));
        quantity(0, material.getQuantity());
        quantity(0, early.getQuantity());
        quantity(20, late.getQuantity());
    }

    @Test
    void consumesShipmentsInArrivalOrderAndKeepsTheSurplus() {
        var first = shipment(4, DUE.minusHours(2));
        var second = shipment(10, DUE.minusHours(1));
        quantity(0, deduct(material(0), 7, second, first));
        quantity(0, first.getQuantity());
        quantity(7, second.getQuantity());
    }

    @Test
    void doesNotAllocateTheSameIncomingQuantityToTwoOrders() {
        var material = material(0);
        var shared = shipment(6, DUE.minusHours(1));
        quantity(0, deduct(material, 4, shared));
        quantity(2, shared.getQuantity());
        quantity(2, deduct(material, 4, shared));
        quantity(0, shared.getQuantity());
    }

    @Test
    void onHandInventoryIsConsumedBeforeIncomingStock() {
        var material = material(12);
        var shipment = shipment(4, DUE.minusHours(1));
        quantity(0, deduct(material, 10, shipment));
        quantity(2, material.getQuantity());
        quantity(4, shipment.getQuantity());
    }

    @Test
    void noIncomingShipmentsRetainsTheExistingShortageBehavior() {
        var material = material(3);
        var order = new ProductionOrder().setDeliveryTime(DUE).setMaterialCode(CODE);
        quantity(7, InventoryUtils.deductInventory(order, material, BigDecimal.TEN, Map.of()));
        quantity(0, material.getQuantity());
    }
}
