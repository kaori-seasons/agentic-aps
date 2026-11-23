package cn.windwheel.aps.solve;

import cn.windwheel.aps.entity.SolveAuditPO;
import cn.windwheel.aps.mapper.SolveAuditMapper;
import cn.windwheel.aps.solve.adaptive.AdaptiveScheduling;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class AdaptiveAuditTest {
    @Test
    void finishingOrFailingRetainsTrialEvidence() throws Exception {
        for (String status : List.of("FEASIBLE","FAILED","CANCELLED")) {
            var captured=new java.util.concurrent.atomic.AtomicReference<SolveAuditPO>();
            var previous=new SolveAuditPO().setResultJson(
                    "{\"adaptive\":{\"rootSeed\":17,\"selectedTrial\":0,\"trials\":[{\"status\":\"FEASIBLE\"}]}}");
            var mapper=(SolveAuditMapper)java.lang.reflect.Proxy.newProxyInstance(getClass().getClassLoader(),
                    new Class<?>[]{SolveAuditMapper.class},(proxy,method,args)-> {
                        if (method.getName().equals("selectOne")) return previous;
                        if (method.getName().equals("update")) { captured.set((SolveAuditPO)args[0]); return 1; }
                        throw new UnsupportedOperationException(method.getName());
                    });
            var json=new ObjectMapper().findAndRegisterModules();
            var service=new SolveAuditService(mapper,json,null);
            service.finish(UUID.randomUUID(),status,Map.of("message","done"));
            var result=json.readTree(captured.get().getResultJson());
            assertEquals(17,result.path("adaptive").path("rootSeed").asInt());
            assertEquals(1,result.path("adaptive").path("trials").size());
            assertEquals(status,captured.get().getStatus());
        }
    }

    @Test
    void missingAuditSessionFailsClosed() {
        var mapper=(SolveAuditMapper)java.lang.reflect.Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[]{SolveAuditMapper.class},(proxy,method,args)->0);
        var service=new SolveAuditService(mapper,new ObjectMapper(),null);
        assertThrows(cn.windwheel.common.exception.BusinessException.class,()->service.recordAdaptive(UUID.randomUUID(),
                new AdaptiveScheduling.Report("JDE_TIMEFOLD_V1",17,-1,"RUNNING",List.of())));
    }
}
