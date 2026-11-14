package cn.windwheel.aps.solve;

import cn.windwheel.aps.entity.*;
import cn.windwheel.aps.entity.dto.*;
import cn.windwheel.aps.entity.dto.planning.*;
import cn.windwheel.aps.entity.enums.*;
import cn.windwheel.aps.graph.*;
import cn.windwheel.aps.solve.config.*;
import cn.windwheel.aps.solve.model.*;
import cn.windwheel.aps.solve.presolve.*;
import cn.windwheel.common.exception.BusinessException;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.*;

import static cn.windwheel.aps.solve.HybridSchedulingTest.*;
import static org.junit.jupiter.api.Assertions.*;

class DynamicSchedulingTest {
    static DynamicSchedulingRequest request() {
        var request = new DynamicSchedulingRequest();
        request.setFreezeUntil(START);
        var event = new DynamicSchedulingRequest.Event();
        event.setType(DynamicSchedulingRequest.EventType.RESOURCE_UNAVAILABLE);
        event.setTargetId("R");
        event.setStart(START.plusHours(1));
        event.setEnd(START.plusHours(2));
        request.setEvents(List.of(event));
        return request;
    }

    static BasicData data() {
        var order = new ProductionOrder();
        order.setId("O1");
        order.setMaterialId("M");
        var graph = new Graph<ProcessRouteNodePropertiesDTO>();
        graph.addNode(new GraphNode<ProcessRouteNodePropertiesDTO>().setId("N").setData(
                new ProcessRouteNodePropertiesDTO().setUseMainResources(List.of(new ProcessUseMainResourcesDTO().setId("R")))));
        return new BasicData().setProductionOrders(new ArrayList<>(List.of(order)))
                .setProductionResources(List.of(new ProductionResource().setId("R"),new ProductionResource().setId("R2")))
                .setProcessRouteMap(Map.of("M",graph)).setBomMap(Map.of()).setMaterialMap(Map.of("M",new Material().setId("M")))
                .setPinnedTasks(new ArrayList<>());
    }

    @Test
    void freezesUnrelatedOrdersAndInProgressWorkWhileKeepingAffectedOrderMovable() {
        var data = data();
        var affected = new PlanTaskAdjustPO().setCode("A").setMainOrderId("O1").setMainResourceId("R")
                .setStartTime(START.plusHours(4)).setPinned(false).setTaskStatus(PlanTaskStatusEnum.PENDING);
        var unrelated = new PlanTaskAdjustPO().setCode("U").setMainOrderId("O2").setMainResourceId("R2")
                .setStartTime(START.plusHours(4)).setPinned(false).setTaskStatus(PlanTaskStatusEnum.PENDING);
        var running = new PlanTaskAdjustPO().setCode("P").setMainOrderId("O1").setMainResourceId("R")
                .setStartTime(START.plusHours(4)).setPinned(false).setTaskStatus(PlanTaskStatusEnum.PARTIALLY_COMPLETED);
        data.setBaselineTasks(List.of(affected,unrelated,running));
        var problem = problem(SolverEngineEnum.TIMEFOLD,PlanningDirectionEnum.FORWARD,SolverProfileEnum.DEFAULT);
        new DynamicSchedulingService().prepare(data,problem.getStrategy(),request());
        assertEquals(Set.of("U","P"),data.getPinnedTasks().stream().map(PlanTaskAdjustPO::getCode).collect(java.util.stream.Collectors.toSet()));
        assertFalse(unrelated.getPinned());
    }

    @Test
    void rejectsUnknownReferencesInvalidWindowsAndNegativeRecommendedWeights() {
        var service = new DynamicSchedulingService();
        var strategy = problem(SolverEngineEnum.TIMEFOLD,PlanningDirectionEnum.FORWARD,SolverProfileEnum.DEFAULT).getStrategy();
        var request = request();
        request.getEvents().get(0).setTargetId("OTHER_TENANT_RESOURCE");
        assertThrows(BusinessException.class,()->service.prepare(data(),strategy,request));
        request.getEvents().get(0).setTargetId("R");
        request.getEvents().get(0).setEnd(START);
        assertThrows(BusinessException.class,()->service.prepare(data(),strategy,request));
        request.getEvents().get(0).setEnd(START.plusHours(2));
        request.setRecommendedWeights(new ConstraintWeightConfigDTO().setMinimizeTardinessWeight(-1));
        assertThrows(IllegalArgumentException.class,()->service.prepare(data(),strategy,request));
    }

    @Test
    void rejectsDateFieldsBelongingToAnotherEventTypeBeforeChangingPriority() {
        var data = data();
        var request = request();
        request.getEvents().get(0).setType(DynamicSchedulingRequest.EventType.INSERT_ORDER);
        request.getEvents().get(0).setTargetId("O1");
        var strategy = problem(SolverEngineEnum.TIMEFOLD,PlanningDirectionEnum.FORWARD,SolverProfileEnum.DEFAULT).getStrategy();
        assertThrows(BusinessException.class,()->new DynamicSchedulingService().prepare(data,strategy,request));
        assertNull(data.getProductionOrders().get(0).getPriority());
    }

    @Test
    void seedsCannotMoveFrozenTasksOrDuplicateTasks() {
        var problem = problem(SolverEngineEnum.TIMEFOLD,PlanningDirectionEnum.FORWARD,SolverProfileEnum.DEFAULT);
        var resource = problem.getMainResources().get(0);
        problem.getTasks().get(0).setPinned(true).setResource(resource).setStartTime(START);
        var request = request();
        var seed = new DynamicSchedulingRequest.Seed();
        seed.setTaskCode("T1");
        seed.setResourceId("R");
        var data = new BasicData().setDynamicRequest(request);
        request.setSeeds(List.of(seed));
        assertThrows(BusinessException.class,()->new DynamicSchedulingService().apply(problem,data));
        seed.setTaskCode("T2");
        request.setSeeds(List.of(seed,seed));
        assertThrows(BusinessException.class,()->new DynamicSchedulingService().apply(problem,data));
    }
}
