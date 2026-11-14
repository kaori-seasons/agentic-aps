package cn.windwheel.aps.solve;

import cn.windwheel.aps.component.PlanningDirectionHandler;
import cn.windwheel.aps.entity.dto.*;
import cn.windwheel.aps.entity.dto.planning.*;
import cn.windwheel.aps.entity.enums.*;
import cn.windwheel.aps.solve.calculate.service.impl.*;
import cn.windwheel.aps.solve.config.*;
import cn.windwheel.aps.solve.model.*;
import cn.windwheel.aps.solve.presolve.DynamicSchedulingService;
import cn.windwheel.aps.solve.score.SchedulingRules;
import cn.windwheel.aps.solve.timefold.TimefoldManageConfigurator;
import cn.windwheel.common.utils.*;
import org.junit.jupiter.api.*;
import org.springframework.context.support.GenericApplicationContext;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class HybridSchedulingTest {
    static final LocalDateTime START = LocalDateTime.of(2026, 11, 13, 8, 0);
    GenericApplicationContext context;

    @BeforeEach
    void setup() {
        context = new GenericApplicationContext();
        context.registerBean(PlanningDirectionHandler.class, () -> new PlanningDirectionHandler(
                List.of(new TaskTimeForwardServiceImpl(), new TaskTimeBackwardServiceImpl())));
        context.refresh();
        new SpringContextUtil().setApplicationContext(context);
        TenantContextHolder.setTenantId("hybrid-test");
    }

    @AfterEach
    void cleanup() {
        context.close();
        TenantContextHolder.clear();
    }

    static ProductionTask task(String code) {
        return new ProductionTask().setId(code).setCode(code).setPinned(false)
                .setMergeTask(false).setStartTask(true).setEndTask(true).setPriority(0)
                .setQuantity(BigDecimal.ONE).setProcess(new ProcessRouteNodePropertiesDTO()
                        .setCode("P").setProcessRelationship(ProcessRelationshipEnum.ES)
                        .setUseMainResources(List.of(new ProcessUseMainResourcesDTO().setId("R").setThroughput("1Hp"))));
    }

    static SchedulingSolution problem(SolverEngineEnum engine, PlanningDirectionEnum direction, SolverProfileEnum profile) {
        var resource = new MainProductionResource().setId("R").setWorkCalendars(List.of());
        var strategy = new PlanningStrategy();
        strategy.setConfig(new StrategyConfigDTO().setDirection(direction).setSolverEngine(engine)
                .setSolverProfile(profile).setRandomSeed(17L).setScoreCalculationCountLimit(100L));
        strategy.setBeginTime(START);
        return new SchedulingSolution().setPlanTime(direction == PlanningDirectionEnum.FORWARD ? START : START.plusHours(12))
                .setStrategy(strategy).setMainResources(new ArrayList<>(List.of(resource)))
                .setProductionOrders(List.of()).setTasks(new ArrayList<>(List.of(task("T1"),task("T2"))));
    }

    @Test
    void bothEnginesSolveBothDirectionsWithBothProfiles() throws Exception {
        for (SolverEngineEnum engine : SolverEngineEnum.values()) {
            for (PlanningDirectionEnum direction : PlanningDirectionEnum.values()) {
                for (SolverProfileEnum profile : List.of(SolverProfileEnum.FFD_TABU, SolverProfileEnum.FFD_LATE_ACCEPTANCE)) {
                    var problem = problem(engine,direction,profile);
                    new DynamicSchedulingService().apply(problem,new BasicData());
                    var result = new SchedulingEngineRouter(new SolveManageConfigurator(),new TimefoldManageConfigurator())
                            .solve(UUID.randomUUID(),problem);
                    assertNotNull(result);
                    assertEquals(0,result.getScore().hardScore(), engine + " " + direction);
                    assertTrue(result.getTasks().stream().allMatch(SchedulingRules::scheduled));
                    assertFalse(SchedulingRules.overlap(result.getTasks().get(0).getStartTime(),result.getTasks().get(0).getEndTime(),
                            result.getTasks().get(1).getStartTime(),result.getTasks().get(1).getEndTime()));
                }
            }
        }
    }

    @Test
    void timefoldPreservesPinnedPrefixAndAvoidsDowntimeAndMaterialDelay() throws Exception {
        var problem = problem(SolverEngineEnum.TIMEFOLD,PlanningDirectionEnum.FORWARD,SolverProfileEnum.FFD_LATE_ACCEPTANCE);
        var resource = problem.getMainResources().get(0);
        var pinned = problem.getTasks().get(0).setPinned(true).setResource(resource).setStartTime(START);
        problem.getTasks().get(1).setNotBefore(START.plusHours(2));
        new DynamicSchedulingService().apply(problem,new BasicData());
        resource.getDowntimes().add(new ResourceDowntime().setResourceId("R").setStart(START.plusHours(2)).setEnd(START.plusHours(4)));
        var result = new SchedulingEngineRouter(new SolveManageConfigurator(),new TimefoldManageConfigurator()).solve(UUID.randomUUID(),problem);
        assertEquals("T1",result.getMainResources().get(0).getTaskList().get(0).getCode());
        var frozen = result.getTasks().stream().filter(t -> t.getCode().equals("T1")).findFirst().orElseThrow();
        var moved = result.getTasks().stream().filter(t -> t.getCode().equals("T2")).findFirst().orElseThrow();
        assertEquals(START,frozen.getStartTime());
        assertEquals(START.plusHours(1),frozen.getEndTime());
        assertFalse(moved.getStartTime().isBefore(START.plusHours(4)));
        assertEquals(0,result.getScore().hardScore());
    }

    @Test
    void frozenFutureTaskLeavesEarlierCapacityUsable() throws Exception {
        for (SolverEngineEnum engine : SolverEngineEnum.values()) {
            var problem = problem(engine,PlanningDirectionEnum.FORWARD,SolverProfileEnum.FFD_LATE_ACCEPTANCE);
            problem.getStrategy().getConfig().setScoreCalculationCountLimit(null).setSolveTimeSeconds(1L)
                    .setMaxNoImprovementTime(1L);
            var resource = problem.getMainResources().get(0);
            problem.getTasks().get(0).setPinned(true).setResource(resource).setStartTime(START.plusHours(8));
            resource.getTaskList().add(problem.getTasks().get(0));
            new DynamicSchedulingService().apply(problem,new BasicData());
            var result = new SchedulingEngineRouter(new SolveManageConfigurator(),new TimefoldManageConfigurator()).solve(UUID.randomUUID(),problem);
            var movable = result.getTasks().stream().filter(t -> t.getCode().equals("T2")).findFirst().orElseThrow();
            assertEquals(START,movable.getStartTime());
            assertEquals(0,result.getScore().initScore(),engine.toString());
            var frozen = result.getTasks().stream().filter(t -> t.getCode().equals("T1")).findFirst().orElseThrow();
            assertEquals(START.plusHours(8),frozen.getStartTime());
            assertEquals("R",frozen.getResource().getId());
        }
    }

    @Test
    void redisCancellationMonitorTerminatesBothEngines() throws Exception {
        for (SolverEngineEnum engine : SolverEngineEnum.values()) {
            var problem = problem(engine,PlanningDirectionEnum.FORWARD,SolverProfileEnum.FFD_LATE_ACCEPTANCE);
            problem.getStrategy().getConfig().setScoreCalculationCountLimit(null).setSolveTimeSeconds(5L);
            var checks = new java.util.concurrent.atomic.AtomicInteger();
            assertNull(new SchedulingEngineRouter(new SolveManageConfigurator(),new TimefoldManageConfigurator())
                    .solve(UUID.randomUUID(),problem,()-> checks.incrementAndGet()>1));
        }
    }

    @Test
    void dependencyConstraintsAndSetupPenaltyAgreeAcrossEngines() {
        var problem = problem(SolverEngineEnum.TIMEFOLD,PlanningDirectionEnum.FORWARD,SolverProfileEnum.DEFAULT);
        var resource = problem.getMainResources().get(0);
        var front = problem.getTasks().get(0).setResource(resource).setStartTime(START);
        var next = problem.getTasks().get(1).setResource(resource).setStartTime(START.plusMinutes(30));
        next.setStartTask(false).setFrontTaskCodes(Set.of(front.getCode()));
        var tfFactory = ai.timefold.solver.core.api.solver.SolverFactory.<SchedulingSolution>create(
                new ai.timefold.solver.core.config.solver.SolverConfig().withSolutionClass(SchedulingSolution.class)
                .withEntityClasses(ProductionTask.class,MainProductionResource.class)
                .withConstraintProviderClass(cn.windwheel.aps.solve.timefold.TimefoldConstraintProvider.class));
        var legacyFactory = org.optaplanner.core.api.solver.SolverFactory.<SchedulingSolution>create(
                new org.optaplanner.core.config.solver.SolverConfig().withSolutionClass(SchedulingSolution.class)
                .withEntityClasses(ProductionTask.class,MainProductionResource.class)
                .withConstraintProviderClass(cn.windwheel.aps.solve.score.ApsHardSoftScoreConstraintProvider.class));
        resource.setTaskList(problem.getTasks());
        var tf = (ai.timefold.solver.core.api.score.buildin.hardsoftlong.HardSoftLongScore)
                ai.timefold.solver.core.api.solver.SolutionManager.create(tfFactory).update(problem, ai.timefold.solver.core.api.solver.SolutionUpdatePolicy.UPDATE_SCORE_ONLY);
        var legacy = (org.optaplanner.core.api.score.buildin.hardsoftlong.HardSoftLongScore)
                org.optaplanner.core.api.solver.SolutionManager.create(legacyFactory).update(problem, org.optaplanner.core.api.solver.SolutionUpdatePolicy.UPDATE_SCORE_ONLY);
        assertTrue(tf.hardScore()<0);
        assertEquals(legacy.hardScore(),tf.hardScore());
        assertEquals(legacy.softScore(),tf.softScore());
    }

    @Test
    void snapshotRoundTripReplaysIdenticalProblem() throws Exception {
        var problem = problem(SolverEngineEnum.TIMEFOLD,PlanningDirectionEnum.FORWARD,SolverProfileEnum.FFD_LATE_ACCEPTANCE);
        var mapper = SolveProblemSnapshot.mapper(new com.fasterxml.jackson.databind.ObjectMapper().findAndRegisterModules());
        var json = mapper.writeValueAsString(SolveProblemSnapshot.from(problem));
        var restored = mapper.readValue(json,SolveProblemSnapshot.class).restore();
        var engine = new SchedulingEngineRouter(new SolveManageConfigurator(),new TimefoldManageConfigurator());
        var first = engine.solve(UUID.randomUUID(),problem);
        var replay = engine.solve(UUID.randomUUID(),restored);
        assertEquals(first.getScore(),replay.getScore());
        assertEquals(first.getMainResources().get(0).getTaskList().stream().map(ProductionTask::getCode).toList(),
                replay.getMainResources().get(0).getTaskList().stream().map(ProductionTask::getCode).toList());
        assertEquals(first.getTasks().get(0).getStartTime(),replay.getTasks().get(0).getStartTime());
    }

    @Test
    void fullAssertDetectsNoScoreCorruptionAcrossResourcesAndDependencies() {
        for (SolverEngineEnum engine : SolverEngineEnum.values()) {
            for (PlanningDirectionEnum direction : PlanningDirectionEnum.values()) {
                var problem = problem(engine,direction,SolverProfileEnum.DEFAULT);
                var other = new MainProductionResource().setId("R2").setWorkCalendars(List.of());
                problem.getMainResources().add(other);
                problem.setTasks(new ArrayList<>());
                for (int i=0;i<3;i++) {
                    var first = task("A"+i).setEndTask(false).setMaterial(new Material().setId("M"+i));
                    var last = task("B"+i).setStartTask(false).setMaterial(first.getMaterial());
                    first.getProcess().setUseMainResources(List.of(new ProcessUseMainResourcesDTO().setId("R").setThroughput("1Hp"),
                            new ProcessUseMainResourcesDTO().setId("R2").setThroughput("2Hp")));
                    last.setProcess(first.getProcess());
                    last.addFrontTask(first);
                    problem.getTasks().add(first);
                    problem.getTasks().add(last);
                    first.getWeights().setMinimizeSetupTimeWeight(5);
                    last.setWeights(first.getWeights());
                }
                var config = new ai.timefold.solver.core.config.solver.SolverConfig().withSolutionClass(SchedulingSolution.class)
                        .withEntityClasses(ProductionTask.class,MainProductionResource.class)
                        .withConstraintProviderClass(cn.windwheel.aps.solve.timefold.TimefoldConstraintProvider.class)
                        .withEnvironmentMode(ai.timefold.solver.core.config.solver.EnvironmentMode.FULL_ASSERT)
                        .withRandomSeed(17L).withMoveThreadCount("NONE")
                        .withTerminationConfig(new ai.timefold.solver.core.config.solver.termination.TerminationConfig()
                                .withScoreCalculationCountLimit(500L));
                if (engine == SolverEngineEnum.TIMEFOLD) {
                    var result = ai.timefold.solver.core.api.solver.SolverFactory.<SchedulingSolution>create(config)
                            .buildSolver().solve(problem);
                    assertEquals(0,result.getTimefoldScore().hardScore());
                } else {
                    var legacyConfig = new org.optaplanner.core.config.solver.SolverConfig().withSolutionClass(SchedulingSolution.class)
                            .withEntityClasses(ProductionTask.class,MainProductionResource.class)
                            .withConstraintProviderClass(cn.windwheel.aps.solve.score.ApsHardSoftScoreConstraintProvider.class)
                            .withEnvironmentMode(org.optaplanner.core.config.solver.EnvironmentMode.FULL_ASSERT)
                            .withRandomSeed(17L).withMoveThreadCount("NONE")
                            .withTerminationConfig(new org.optaplanner.core.config.solver.termination.TerminationConfig()
                                    .withScoreCalculationCountLimit(500L));
                    var result = org.optaplanner.core.api.solver.SolverFactory.<SchedulingSolution>create(legacyConfig)
                            .buildSolver().solve(problem);
                    assertEquals(0,result.getScore().hardScore());
                }
            }
        }
    }

    @Test
    void weightsAreOwnedByProblemAndDoNotDependOnCallingThread() {
        var problem = problem(SolverEngineEnum.TIMEFOLD,PlanningDirectionEnum.FORWARD,SolverProfileEnum.DEFAULT);
        var resource = problem.getMainResources().get(0);
        resource.setTaskList(problem.getTasks());
        var order = new ProductionOrder();
        order.setDeliveryTime(START);
        problem.getTasks().get(0).setResource(resource).setStartTime(START).setOrder(order);
        problem.getTasks().get(1).setResource(resource).setStartTime(START.plusHours(1)).setOrder(order);
        for (var task : problem.getTasks()) {
            task.setWeights(new ConstraintWeightConfigDTO().setFinishEarlyWeight(0).setBalanceResourceLoadWeight(0)
                    .setPriorityWeight(0).setMinimizeTardinessWeight(3));
        }
        var factory = ai.timefold.solver.core.api.solver.SolverFactory.<SchedulingSolution>create(
                new ai.timefold.solver.core.config.solver.SolverConfig().withSolutionClass(SchedulingSolution.class)
                .withEntityClasses(ProductionTask.class,MainProductionResource.class)
                .withConstraintProviderClass(cn.windwheel.aps.solve.timefold.TimefoldConstraintProvider.class));
        var score = (ai.timefold.solver.core.api.score.buildin.hardsoftlong.HardSoftLongScore) ai.timefold.solver.core.api.solver.SolutionManager.create(factory).update(problem);
        assertEquals(-540,score.softScore());
        assertEquals(0,score.hardScore());
    }
}
