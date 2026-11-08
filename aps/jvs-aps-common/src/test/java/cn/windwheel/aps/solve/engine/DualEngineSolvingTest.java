package cn.windwheel.aps.solve.engine;

import cn.windwheel.aps.component.PlanningDirectionHandler;
import cn.windwheel.aps.entity.dto.ProcessRouteNodePropertiesDTO;
import cn.windwheel.aps.entity.dto.ProcessUseMainResourcesDTO;
import cn.windwheel.aps.entity.dto.planning.StrategyConfigDTO;
import cn.windwheel.aps.entity.enums.PlanningDirectionEnum;
import cn.windwheel.aps.entity.enums.ProcessRelationshipEnum;
import cn.windwheel.aps.entity.enums.SolverProfileEnum;
import cn.windwheel.aps.solve.calculate.service.impl.TaskTimeBackwardServiceImpl;
import cn.windwheel.aps.solve.calculate.service.impl.TaskTimeForwardServiceImpl;
import cn.windwheel.aps.solve.model.*;
import cn.windwheel.common.utils.SpringContextUtil;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.GenericApplicationContext;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** Real jobs and real listeners, without database/Redis/server dependencies. */
class DualEngineSolvingTest {
    private static GenericApplicationContext context;
    private static ApplicationContext previousContext;
    private static final LocalDateTime BEGIN = LocalDateTime.of(2026, 1, 5, 8, 0);

    @BeforeAll
    static void setUpCalendarServices() {
        previousContext = SpringContextUtil.getApplicationContext();
        context = new GenericApplicationContext();
        context.registerBean(PlanningDirectionHandler.class, () -> new PlanningDirectionHandler(List.of(
                new TaskTimeForwardServiceImpl(), new TaskTimeBackwardServiceImpl())));
        context.refresh();
        new SpringContextUtil().setApplicationContext(context);
    }

    @AfterAll
    static void closeContext() {
        new SpringContextUtil().setApplicationContext(previousContext);
        context.close();
    }

    @ParameterizedTest
    @EnumSource(SolverProfileEnum.class)
    @Timeout(20)
    void everyPresetActuallySolvesForwardAndBackward(SolverProfileEnum profile) throws Exception {
        for (PlanningDirectionEnum direction : PlanningDirectionEnum.values()) {
            SchedulingSolution problem = problem(profile, direction);
            PlanningSolverEngine engine = profile.isTimefold() ? new TimefoldV1Engine() : new OptaPlannerEngine();
            try (ManagedSolverSession session = engine.createSession(problem.getStrategy().getConfig())) {
                SchedulingSolution result = session.solve(UUID.randomUUID(), problem);
                assertFeasible(result);
                assertNotSame(problem, result, "The solver must return a planning clone");
                if (profile.isTimefold()) assertNotNull(result.getTimefoldScore());
                for (MainProductionResource resource : result.getMainResources()) {
                    ProductionTask previous = null;
                    for (ProductionTask task : resource.getTaskList()) {
                        assertSame(resource, task.getResource());
                        if (previous != null) assertFalse(previous.getEndTime().isAfter(task.getStartTime()));
                        previous = task;
                    }
                }
                if (direction == PlanningDirectionEnum.FORWARD) {
                    result.getTasks().forEach(task -> assertFalse(task.getStartTime().isBefore(BEGIN)));
                } else {
                    result.getTasks().forEach(task -> assertFalse(task.getEndTime().isAfter(BEGIN.plusHours(8))));
                }
            }
        }
    }

    @ParameterizedTest
    @EnumSource(value = SolverProfileEnum.class, names = {"TF_TABU", "TF_LATE_ACCEPTANCE", "TF_SIMULATED_ANNEALING", "TF_GREAT_DELUGE", "TF_TABU_OSCILLATION"})
    @Timeout(20)
    void timefoldPreservesLockedResourceAndTimestamp(SolverProfileEnum profile) throws Exception {
        SchedulingSolution problem = problem(profile, PlanningDirectionEnum.FORWARD);
        ProductionTask locked = problem.getTasks().get(0);
        MainProductionResource resource = problem.getMainResources().get(0);
        locked.setPinned(true).setResource(resource).setStartTime(BEGIN);
        resource.getTaskList().add(locked);
        try (ManagedSolverSession session = new TimefoldV1Engine().createSession(problem.getStrategy().getConfig())) {
            SchedulingSolution result = session.solve(UUID.randomUUID(), problem);
            assertFeasible(result);
            ProductionTask solved = result.getTasks().stream().filter(task -> task.getId().equals(locked.getId())).findFirst().orElseThrow();
            assertEquals("R1", solved.getResource().getId());
            assertEquals(BEGIN, solved.getStartTime());
            assertEquals(1, solved.getResource().getPinnedTaskCount());
            assertSame(solved, solved.getResource().getTaskList().get(0));
        }
    }

    @Test
    @Timeout(20)
    void bothEnginesCanSolveConcurrentlyWithoutSharingPlanningState() throws Exception {
        SchedulingSolution oldProblem = problem(SolverProfileEnum.FFD_TABU, PlanningDirectionEnum.FORWARD);
        SchedulingSolution newProblem = problem(SolverProfileEnum.TF_TABU, PlanningDirectionEnum.FORWARD);
        var executor = java.util.concurrent.Executors.newFixedThreadPool(2);
        try (ManagedSolverSession legacy = new OptaPlannerEngine().createSession(oldProblem.getStrategy().getConfig());
             ManagedSolverSession timefold = new TimefoldV1Engine().createSession(newProblem.getStrategy().getConfig())) {
            var oldFuture = executor.submit(() -> legacy.solve(UUID.randomUUID(), oldProblem));
            var newFuture = executor.submit(() -> timefold.solve(UUID.randomUUID(), newProblem));
            SchedulingSolution oldResult = oldFuture.get(10, java.util.concurrent.TimeUnit.SECONDS);
            SchedulingSolution newResult = newFuture.get(10, java.util.concurrent.TimeUnit.SECONDS);
            assertFeasible(oldResult);
            assertFeasible(newResult);
            assertNull(oldResult.getTimefoldScore());
            assertNotNull(newResult.getTimefoldScore());
            assertNotSame(oldResult.getTasks().get(0), newResult.getTasks().get(0));
        } finally {
            executor.shutdownNow();
        }
    }

    @ParameterizedTest
    @EnumSource(value = SolverProfileEnum.class, names = {"FFD_TABU", "TF_TABU"})
    @Timeout(20)
    void enginesHonorCrossResourcePrecedence(SolverProfileEnum profile) throws Exception {
        for (PlanningDirectionEnum direction : PlanningDirectionEnum.values()) {
            SchedulingSolution problem = problem(profile, direction);
            ProductionTask predecessor = problem.getTasks().get(0);
            ProductionTask successor = problem.getTasks().get(1);
            predecessor.setEndTask(false).setProcess(new ProcessRouteNodePropertiesDTO().setCode("P-FIRST")
                    .setProcessRelationship(ProcessRelationshipEnum.ES)
                    .setUseMainResources(List.of(new ProcessUseMainResourcesDTO().setId("R1").setThroughput("1Hp"))));
            successor.setStartTask(false).setProcess(new ProcessRouteNodePropertiesDTO().setCode("P-NEXT")
                    .setProcessRelationship(ProcessRelationshipEnum.ES)
                    .setUseMainResources(List.of(new ProcessUseMainResourcesDTO().setId("R2").setThroughput("1Hp"))));
            successor.addFrontTask(predecessor);
            PlanningSolverEngine engine = profile.isTimefold() ? new TimefoldV1Engine() : new OptaPlannerEngine();
            try (ManagedSolverSession session = engine.createSession(problem.getStrategy().getConfig())) {
                SchedulingSolution result = session.solve(UUID.randomUUID(), problem);
                assertFeasible(result);
                ProductionTask first = result.getTasks().stream().filter(task -> task.getId().equals("T0")).findFirst().orElseThrow();
                ProductionTask next = result.getTasks().stream().filter(task -> task.getId().equals("T1")).findFirst().orElseThrow();
                assertEquals("R1", first.getResource().getId());
                assertEquals("R2", next.getResource().getId());
                assertFalse(first.getEndTime().isAfter(next.getStartTime()));
                assertSame(first, next.getFrontTasks().iterator().next(), "Dependency must point to the planning clone");
            }
        }
    }

    @Test
    void timefoldRejectsLockedTasksWithoutAssignmentsBeforeSubmission() {
        SchedulingSolution problem = problem(SolverProfileEnum.TF_TABU, PlanningDirectionEnum.FORWARD);
        problem.getTasks().get(0).setPinned(true);
        assertThrows(cn.windwheel.common.exception.BusinessException.class,
                () -> TimefoldV1Engine.preparePinnedTasks(problem));
    }

    private static void assertFeasible(SchedulingSolution result) {
        assertNotNull(result);
        assertNotNull(result.getScore());
        assertTrue(result.getScore().isFeasible(), () -> "Not feasible: " + result.getScore());
        assertEquals(4, result.getMainResources().stream().mapToInt(resource -> resource.getTaskList().size()).sum());
        assertEquals(4, result.getMainResources().stream().flatMap(resource -> resource.getTaskList().stream())
                .map(ProductionTask::getId).distinct().count());
        for (ProductionTask task : result.getTasks()) {
            assertNotNull(task.getResource());
            assertNotNull(task.getStartTime());
            assertNotNull(task.getEndTime());
        }
    }

    private static SchedulingSolution problem(SolverProfileEnum profile, PlanningDirectionEnum direction) {
        StrategyConfigDTO config = new StrategyConfigDTO().setSolverProfile(profile).setDirection(direction)
                .setRandomSeed(7L).setMaxSolveTimeSeconds(5L).setMaxNoImprovementTime(1L).setAcceptedCountLimit(4);
        PlanningStrategy strategy = new PlanningStrategy();
        strategy.setConfig(config);
        ProductionOrder order = new ProductionOrder().setId("O1").setCode("O1").setDeliveryTime(BEGIN.plusHours(8));
        List<MainProductionResource> resources = new ArrayList<>();
        resources.add(new MainProductionResource().setId("R1").setCode("R1").setWorkCalendars(List.of()));
        resources.add(new MainProductionResource().setId("R2").setCode("R2").setWorkCalendars(List.of()));
        ProcessRouteNodePropertiesDTO process = new ProcessRouteNodePropertiesDTO().setCode("P1")
                .setProcessRelationship(ProcessRelationshipEnum.ES)
                .setUseMainResources(List.of(new ProcessUseMainResourcesDTO().setId("R1").setThroughput("1Hp"),
                        new ProcessUseMainResourcesDTO().setId("R2").setThroughput("1Hp")));
        List<ProductionTask> tasks = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            tasks.add(new ProductionTask().setId("T" + i).setCode("T" + i).setPriority(i + 1)
                    .setPinned(false).setStartTask(true).setEndTask(true).setProcess(process)
                    .setOrder(order).setQuantity(BigDecimal.ONE));
        }
        return new SchedulingSolution().setStrategy(strategy).setMainResources(resources).setTasks(tasks)
                .setProductionOrders(List.of(order))
                .setPlanTime(direction == PlanningDirectionEnum.FORWARD ? BEGIN : BEGIN.plusHours(8));
    }
}
