package cn.windwheel.aps.solve;

import cn.windwheel.aps.entity.SolveAuditPO;
import cn.windwheel.aps.mapper.SolveAuditMapper;
import cn.windwheel.common.exception.BusinessException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import java.lang.reflect.Proxy;

import static org.junit.jupiter.api.Assertions.*;

class SolveAuditServiceTest {
    final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void sourceSignatureDetectsOrderAndInventoryChanges() {
        var service = new SolveAuditService(null,mapper,null);
        var data = DynamicSchedulingTest.data();
        String signature = service.signature(data);
        assertEquals(signature,service.signature(data));
        data.getProductionOrders().get(0).setDeliveryTime(HybridSchedulingTest.START);
        assertNotEquals(signature,service.signature(data));
        signature = service.signature(data);
        data.getMaterialMap().get("M").setQuantity(java.math.BigDecimal.TEN);
        assertNotEquals(signature,service.signature(data));
    }

    @Test
    void publicationRejectsStaleSnapshot() throws Exception {
        var data = DynamicSchedulingTest.data();
        var reference = new SolveAuditService(null,mapper,null).signature(data);
        var audit = new SolveAuditPO().setInputJson(mapper.writeValueAsString(
                java.util.Map.of("basicData",java.util.Map.of("sourceSignature",reference))));
        var proxy = (SolveAuditMapper)Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[]{SolveAuditMapper.class},(object,method,args)->audit);
        var service = new SolveAuditService(proxy,mapper,null);
        service.validateBeforeConfirm(data);
        data.getProductionOrders().get(0).setDeliveryTime(HybridSchedulingTest.START);
        assertThrows(BusinessException.class,()->service.validateBeforeConfirm(data));
    }
}
