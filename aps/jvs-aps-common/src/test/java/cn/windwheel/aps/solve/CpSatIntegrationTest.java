package cn.windwheel.aps.solve;
import cn.windwheel.aps.cpsat.Protocol;
import cn.windwheel.aps.entity.enums.*;
import cn.windwheel.aps.solve.cpsat.CpSatProblemMapper;
import cn.windwheel.aps.solve.model.*;
import cn.windwheel.aps.solve.presolve.DynamicSchedulingService;
import cn.windwheel.aps.solve.config.SolveManageConfigurator;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CpSatIntegrationTest {
    @Test void serverGeneratedCurrentTimeUsesThePersistedMillisecondClock() throws Exception {
        var p=problem();p.getStrategy().setBeginTime(LocalDateTime.now().minusDays(1));
        var service=new cn.windwheel.aps.solve.impl.SolveServiceImpl(null,null,null,null,null,null);
        var method=service.getClass().getDeclaredMethod("calculatePlanTime",PlanningStrategy.class,List.class);
        method.setAccessible(true);
        LocalDateTime captured=(LocalDateTime)method.invoke(service,p.getStrategy(),List.of());
        assertEquals(0,captured.getNano()%1000000);
        assertTrue(captured.isAfter(LocalDateTime.now().minusMinutes(1)));
    }
    @Test void frozenReplacementCanonicalizesDependencyReferences() {
        var p=problem();var predecessor=p.getTasks().get(0);var successor=p.getTasks().get(1);
        successor.addFrontTask(predecessor);
        successor.getFrontTasks().add(new ProductionTask().setId("stale-generated-id").setCode(predecessor.getCode()));
        assertEquals(2,successor.getFrontTasks().size());
        new DynamicSchedulingService().apply(p,new BasicData());
        assertEquals(Set.of(predecessor),successor.getFrontTasks());
        var request=CpSatProblemMapper.map(UUID.randomUUID(),p,5,100,()->false);
        assertEquals(1,request.tasks().stream().filter(t->t.id().equals(successor.getCode())).findFirst().orElseThrow().dependencies().size());
    }
    @Test void frozenTaskRestoresProductIdentityForAllEngines() throws Exception {
        var order=new ProductionOrder();order.setId("order");order.setMaterialId("finished");
        var material=new Material().setId("finished");
        var frozen=new cn.windwheel.aps.entity.PlanTaskAdjustPO().setCode("frozen")
            .setProductionOrderId("order").setMainResourceId("R").setScheduledQuantity(java.math.BigDecimal.ONE)
            .setStartTime(HybridSchedulingTest.START).setEndTime(HybridSchedulingTest.START.plusHours(1));
        var service=new cn.windwheel.aps.solve.impl.SolveServiceImpl(null,null,null,null,null,null);
        var method=service.getClass().getDeclaredMethod("convertPinnedTask",List.class,List.class,List.class,Map.class);
        method.setAccessible(true);
        @SuppressWarnings("unchecked")
        var tasks=(List<ProductionTask>)method.invoke(service,List.of(new MainProductionResource().setId("R")),
            List.of(frozen),List.of(order),Map.of("finished",material));
        assertSame(material,tasks.get(0).getMaterial());
        assertSame(order,tasks.get(0).getOrder());
        assertEquals(frozen.getEndTime(),tasks.get(0).getFrozenEndTime());
    }
    private SchedulingSolution problem() {
        var p=HybridSchedulingTest.problem(SolverEngineEnum.ORTOOLS_CP_SAT,PlanningDirectionEnum.FORWARD,SolverProfileEnum.DEFAULT);
        p.getStrategy().getConfig().setMaterialConstrained(true).setScoreCalculationCountLimit(null).setMaxNoImprovementTime(0L);
        p.getTasks().forEach(t->t.setMaterial(new Material().setId("finished")));
        new DynamicSchedulingService().apply(p,new BasicData());
        return p;
    }
    @Test void mapsCalendarAndOnlyAcceptsACompleteFeasibleBaseline() {
        var p=problem();var resource=p.getMainResources().get(0);
        p.getTasks().get(0).setResource(resource).setStartTime(HybridSchedulingTest.START);
        p.getTasks().get(1).setResource(resource).setStartTime(HybridSchedulingTest.START.plusHours(3));
        var request=CpSatProblemMapper.map(UUID.randomUUID(),p,5,100,()->false);
        assertEquals(2,request.baseline().size()); assertTrue(Protocol.verify(request,request.baseline())>0);
        p.getTasks().get(1).setStartTime(HybridSchedulingTest.START);
        assertTrue(CpSatProblemMapper.map(UUID.randomUUID(),p,5,100,()->false).baseline().isEmpty());
    }
    @Test void failsClosedForSupplyShortageUnsupportedSettingsAndCancellation() {
        var p=problem();var data=new BasicData();data.getUnreservedPurchasedMaterials().add("missing");
        assertThrows(IllegalArgumentException.class,()->CpSatProblemMapper.prepare(p,data));
        p.getTasks().get(0).setMaterialDelayMap(Map.of("raw",Duration.ofHours(1)));
        assertThrows(IllegalArgumentException.class,()->CpSatProblemMapper.map(UUID.randomUUID(),p,5,100,()->false));
        p.getTasks().get(0).setMaterialDelayMap(null);
        assertThrows(java.util.concurrent.CancellationException.class,()->CpSatProblemMapper.map(UUID.randomUUID(),p,5,100,()->true));
        assertDoesNotThrow(()->SolveManageConfigurator.validate(p.getStrategy().getConfig()));
        p.getStrategy().getConfig().setDirection(PlanningDirectionEnum.BACKWARD);
        assertThrows(IllegalArgumentException.class,()->SolveManageConfigurator.validate(p.getStrategy().getConfig()));
    }
    @Test void carriesActuallyReservedArrivalIntoTaskRelease() {
        var p=problem();var data=new BasicData();var t=p.getTasks().get(0);t.setMainOrderId("order");
        var arrival=HybridSchedulingTest.START.plusDays(1);
        data.getReservedMaterialAvailableAt().put("order",arrival);
        CpSatProblemMapper.prepare(p,data);assertEquals(arrival,t.getNotBefore());
        var r=CpSatProblemMapper.map(UUID.randomUUID(),p,5,100,()->false);
        assertEquals(arrival.toInstant(ZoneOffset.UTC).toEpochMilli()-r.originEpochMillis(),r.tasks().stream().filter(x->x.id().equals(t.getCode())).findFirst().orElseThrow().release());
    }
    @Test void sixBusinessCostsEqualTimefoldWithoutRecomputingTimes() {
        var p=problem();var r=p.getMainResources().get(0);var a=p.getTasks().get(0);var b=p.getTasks().get(1);
        var w=p.getStrategy().getConfig().getConstraintWeightConfig();
        w.setPriorityWeight(7).setFinishEarlyWeight(2).setMinimizeTardinessWeight(3)
                .setBalanceResourceLoadWeight(4).setMinimizeGapBetweenDependentTasksWeight(5).setMinimizeSetupTimeWeight(6);
        var order=new ProductionOrder();order.setDeliveryTime(HybridSchedulingTest.START.plusMinutes(30));
        a.setResource(r).setStartTime(HybridSchedulingTest.START).setPriority(2).setOrder(order).setWeights(w);
        b.setResource(r).setStartTime(HybridSchedulingTest.START.plusHours(3)).setPriority(1).setOrder(order).setWeights(w)
                .setMaterial(new Material().setId("different"));b.addFrontTask(a);r.setTaskList(p.getTasks());
        var request=CpSatProblemMapper.map(UUID.randomUUID(),p,5,100,()->false);
        long cost=Protocol.verify(request,request.baseline());
        var factory=ai.timefold.solver.core.api.solver.SolverFactory.<SchedulingSolution>create(
            new ai.timefold.solver.core.config.solver.SolverConfig().withSolutionClass(SchedulingSolution.class)
                .withEntityClasses(ProductionTask.class,MainProductionResource.class)
                .withConstraintProviderClass(cn.windwheel.aps.solve.timefold.TimefoldConstraintProvider.class));
        var score=(ai.timefold.solver.core.api.score.buildin.hardsoftlong.HardSoftLongScore)
            ai.timefold.solver.core.api.solver.SolutionManager.create(factory).update(p,ai.timefold.solver.core.api.solver.SolutionUpdatePolicy.UPDATE_SCORE_ONLY);
        assertEquals(0,score.hardScore());assertEquals(-cost,score.softScore());
        assertEquals(HybridSchedulingTest.START.plusHours(3),b.getStartTime());
    }
}
