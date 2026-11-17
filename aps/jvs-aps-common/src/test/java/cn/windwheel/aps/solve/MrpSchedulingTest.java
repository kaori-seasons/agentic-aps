package cn.windwheel.aps.solve;

import cn.windwheel.aps.component.PlanningDirectionHandler;
import cn.windwheel.aps.entity.dto.ProcessRouteNodePropertiesDTO;
import cn.windwheel.aps.entity.dto.ProcessUseMainResourcesDTO;
import cn.windwheel.aps.entity.enums.*;
import cn.windwheel.aps.graph.Graph;
import cn.windwheel.aps.graph.GraphNode;
import cn.windwheel.aps.solve.calculate.service.impl.TaskTimeBackwardServiceImpl;
import cn.windwheel.aps.solve.calculate.service.impl.TaskTimeForwardServiceImpl;
import cn.windwheel.aps.solve.config.SchedulingEngineRouter;
import cn.windwheel.aps.solve.config.SolveManageConfigurator;
import cn.windwheel.aps.solve.model.*;
import cn.windwheel.aps.solve.presolve.DynamicSchedulingService;
import cn.windwheel.aps.solve.score.SchedulingRules;
import cn.windwheel.aps.solve.timefold.TimefoldManageConfigurator;
import cn.windwheel.aps.solve.util.MrpUtils;
import cn.windwheel.aps.solve.util.ProductionTaskUtils;
import cn.windwheel.common.utils.SpringContextUtil;
import cn.windwheel.common.utils.TenantContextHolder;
import org.junit.jupiter.api.*;
import org.springframework.context.support.GenericApplicationContext;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;

import static cn.windwheel.aps.solve.HybridSchedulingTest.START;
import static org.junit.jupiter.api.Assertions.*;

/** Exercises the actual MRP -> task generation -> engine path for incoming stock. */
class MrpSchedulingTest {
    private static final LocalDateTime DUE = START.plusHours(12);
    private static final String FINISHED = "finished-id";
    private static final String COMPONENT = "component-id";
    private GenericApplicationContext context;

    private enum ShipmentCase { EARLY, LATE, PARTIAL }

    @BeforeEach
    void setup() {
        context = new GenericApplicationContext();
        context.registerBean(PlanningDirectionHandler.class, () -> new PlanningDirectionHandler(
                List.of(new TaskTimeForwardServiceImpl(), new TaskTimeBackwardServiceImpl())));
        context.refresh();
        new SpringContextUtil().setApplicationContext(context);
        TenantContextHolder.setTenantId("mrp-regression-test");
    }

    @AfterEach
    void cleanup() {
        context.close();
        TenantContextHolder.clear();
    }

    private static Graph<ProcessRouteNodePropertiesDTO> route(String code) {
        var graph = new Graph<ProcessRouteNodePropertiesDTO>();
        graph.addNode(new GraphNode<ProcessRouteNodePropertiesDTO>().setId(code).setData(
                new ProcessRouteNodePropertiesDTO().setCode(code).setProcessRelationship(ProcessRelationshipEnum.ES)
                        .setUseMainResources(List.of(new ProcessUseMainResourcesDTO().setId("R").setThroughput("10pH")))));
        return graph;
    }

    private static BasicData data(ShipmentCase scenario) {
        var finished = new Material().setId(FINISHED).setCode("FG-100").setSource(MaterialSourceEnum.PRODUCED)
                .setQuantity(BigDecimal.ZERO);
        var component = new Material().setId(COMPONENT).setCode("SF-200").setSource(MaterialSourceEnum.PRODUCED)
                .setQuantity(scenario == ShipmentCase.PARTIAL ? BigDecimal.valueOf(3) : BigDecimal.ZERO)
                .setBufferTimeDuration(Duration.ZERO);
        var bom = new BomMaterial();
        bom.setMaterialId(COMPONENT);
        bom.setQuantity(BigDecimal.valueOf(2));
        var early = new IncomingMaterialOrder();
        early.setMaterialId(COMPONENT);
        early.setMaterialCode(component.getCode());
        early.setDeliveryTime(START.minusHours(1));
        early.setQuantity(scenario == ShipmentCase.PARTIAL ? BigDecimal.valueOf(4) : BigDecimal.TEN);
        var late = new IncomingMaterialOrder();
        late.setMaterialId(COMPONENT);
        late.setMaterialCode(component.getCode());
        late.setDeliveryTime(DUE.plusSeconds(1));
        late.setQuantity(BigDecimal.TEN);
        List<IncomingMaterialOrder> incoming = switch (scenario) {
            case EARLY -> List.of(early);
            case LATE -> List.of(late);
            case PARTIAL -> List.of(late, early);
        };
        return new BasicData().setMaterialMap(Map.of(FINISHED, finished, COMPONENT, component))
                .setBomMap(Map.of(FINISHED, List.of(bom)))
                .setProcessRouteMap(Map.of(FINISHED, route("ASSEMBLE"), COMPONENT, route("MAKE")))
                .setIncomingMaterialOrderMap(Map.of(COMPONENT, incoming));
    }

    private static ProductionOrder order() {
        return new ProductionOrder().setId("order-id").setCode("PO-MRP").setMaterialId(FINISHED)
                .setMaterialCode("FG-100").setQuantity(BigDecimal.valueOf(5)).setDeliveryTime(DUE)
                .setPriority(1).setSupplement(false);
    }

    private void verifyScheduling(ShipmentCase scenario, int componentShortage) throws Exception {
        for (SolverEngineEnum engine : SolverEngineEnum.values()) {
            for (PlanningDirectionEnum direction : PlanningDirectionEnum.values()) {
                var data = data(scenario);
                var order = order();
                var graph = MrpUtils.calculateMaterialAvailability(true, order, data);
                assertEquals(componentShortage == 0 ? 1 : 2, graph.getNodes().size());
                if (componentShortage > 0) {
                    var missing = graph.getNodes().stream().map(GraphNode::getData)
                            .filter(material -> COMPONENT.equals(material.getId())).findFirst().orElseThrow();
                    assertEquals(0, BigDecimal.valueOf(componentShortage)
                            .compareTo(missing.getQuantity()));
                }
                var tasks = ProductionTaskUtils.generateTask(order, data, graph, List.of());
                assertEquals(componentShortage == 0 ? 1 : 2, tasks.size());
                var assembly = tasks.stream().filter(task -> FINISHED.equals(task.getMaterial().getId()))
                        .findFirst().orElseThrow();
                assertEquals(0, BigDecimal.valueOf(5).compareTo(assembly.getQuantity()));
                if (componentShortage > 0) {
                    var make = tasks.stream().filter(task -> COMPONENT.equals(task.getMaterial().getId()))
                            .findFirst().orElseThrow();
                    assertEquals(0, BigDecimal.valueOf(componentShortage).compareTo(make.getQuantity()));
                    assertTrue(make.getSupplement());
                    assertTrue(assembly.getFrontTaskCodes().contains(make.getCode()));
                }
                var problem = HybridSchedulingTest.problem(engine, direction, SolverProfileEnum.FFD_LATE_ACCEPTANCE)
                        .setTasks(tasks).setProductionOrders(List.of(order));
                // One-task schedules have no local-search moves; use a bounded wall-clock budget.
                problem.getStrategy().getConfig().setScoreCalculationCountLimit(null)
                        .setSolveTimeSeconds(1L).setMaxNoImprovementTime(1L);
                new DynamicSchedulingService().apply(problem, data);
                var solved = new SchedulingEngineRouter(new SolveManageConfigurator(), new TimefoldManageConfigurator())
                        .solve(UUID.randomUUID(), problem);
                assertEquals(0, solved.getScore().initScore(), engine + " " + direction);
                assertEquals(0, solved.getScore().hardScore(), engine + " " + direction);
                assertTrue(solved.getTasks().stream().allMatch(SchedulingRules::scheduled));
                var scheduled = solved.getTasks().stream().sorted(Comparator.comparing(ProductionTask::getStartTime)).toList();
                for (int i = 1; i < scheduled.size(); i++) {
                    assertFalse(scheduled.get(i).getStartTime().isBefore(scheduled.get(i - 1).getEndTime()));
                }
                if (componentShortage > 0) {
                    var make = solved.getTasks().stream().filter(task -> COMPONENT.equals(task.getMaterial().getId()))
                            .findFirst().orElseThrow();
                    var assemble = solved.getTasks().stream().filter(task -> FINISHED.equals(task.getMaterial().getId()))
                            .findFirst().orElseThrow();
                    assertFalse(assemble.getStartTime().isBefore(make.getEndTime()));
                }
            }
        }
    }

    @Test void earlyArrivalAvoidsUnnecessaryComponentProduction() throws Exception {
        verifyScheduling(ShipmentCase.EARLY, 0);
    }

    @Test void lateArrivalRetainsTheEntireComponentProductionRequirement() throws Exception {
        verifyScheduling(ShipmentCase.LATE, 10);
    }

    @Test void partialArrivalSchedulesOnlyTheNetComponentShortage() throws Exception {
        verifyScheduling(ShipmentCase.PARTIAL, 3);
    }

    @Test void disablingMaterialConstraintsStillSkipsBomAndStockAllocation() {
        var data = data(ShipmentCase.PARTIAL);
        var graph = MrpUtils.calculateMaterialAvailability(false, order(), data);
        assertEquals(1, graph.getNodes().size());
        assertEquals(0, BigDecimal.valueOf(3).compareTo(data.getMaterialMap().get(COMPONENT).getQuantity()));
        var incoming = data.getIncomingMaterialOrderMap().get(COMPONENT);
        assertEquals(0, BigDecimal.TEN.compareTo(incoming.get(0).getQuantity()));
        assertEquals(0, BigDecimal.valueOf(4).compareTo(incoming.get(1).getQuantity()));
    }
}
