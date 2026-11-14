package cn.windwheel.aps.solve;

import cn.windwheel.aps.solve.model.*;
import cn.windwheel.aps.solve.util.SchedulingOrderNormalizer;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class SchedulingOrderNormalizerTest {
    @Test void frozenAndRegeneratedTasksShareOneCurrentSupplementOrder() {
        var old = new ProductionOrder().setId("old").setCode("PO-SF").setQuantity(BigDecimal.TEN);
        var current = new ProductionOrder().setId("new").setCode("PO-SF").setQuantity(BigDecimal.valueOf(20));
        var frozen = new ProductionTask().setPinned(true).setSupplement(true).setOrder(old);
        var fresh = new ProductionTask().setPinned(false).setSupplement(true).setOrder(current);
        List<ProductionOrder> orders = new ArrayList<>(List.of(new ProductionOrder().setCode("PO")));
        SchedulingOrderNormalizer.appendSupplementOrders(orders,List.of(frozen,fresh));
        assertEquals(2,orders.size());
        assertSame(current,frozen.getOrder());
        assertSame(current,fresh.getOrder());
        assertEquals(BigDecimal.valueOf(20),orders.get(1).getQuantity());
        SchedulingOrderNormalizer.appendSupplementOrders(orders,List.of(frozen,fresh));
        assertEquals(2,orders.size());
    }
}
