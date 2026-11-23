package cn.windwheel.aps.service.facade.impl;

import cn.windwheel.aps.solve.model.ProductionOrder;
import cn.windwheel.aps.solve.model.ProductionTask;
import cn.windwheel.common.exception.BusinessException;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class PlanSolutionOrderIdentityTest {
    @Test void clonedSupplementOrdersUsePersistentIdentityBeforePreviewAndConfirmation() {
        var persisted = new ProductionOrder().setCode("PO-SF").setId("existing");
        var cloneA = new ProductionOrder().setCode("PO-SF").setId("generated");
        var cloneB = new ProductionOrder().setCode("PO-SF").setId("generated");
        var a = new ProductionTask().setCode("cut").setMainOrderId("main").setOrder(cloneA);
        var b = new ProductionTask().setCode("weld").setMainOrderId("main").setOrder(cloneB);
        PlanSolutionFacadeServiceImpl.alignTaskOrderIds(List.of(persisted),List.of(a,b));
        assertEquals("existing",a.getOrder().getId());
        assertEquals("existing",b.getOrder().getId());
        assertEquals("main",a.getMainOrderId());
        assertNotSame(persisted,cloneA);
        assertThrows(BusinessException.class,()->PlanSolutionFacadeServiceImpl.alignTaskOrderIds(
                List.of(persisted),List.of(new ProductionTask().setOrder(new ProductionOrder().setCode("unknown")))));
    }
}
