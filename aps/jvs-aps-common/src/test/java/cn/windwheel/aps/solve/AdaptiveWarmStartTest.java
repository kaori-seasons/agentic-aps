package cn.windwheel.aps.solve;

import cn.windwheel.aps.entity.dto.planning.AdaptiveSolveConfig;
import cn.windwheel.aps.entity.enums.*;
import cn.windwheel.aps.solve.adaptive.*;
import cn.windwheel.aps.solve.config.*;
import cn.windwheel.aps.solve.model.*;
import cn.windwheel.aps.solve.presolve.DynamicSchedulingService;
import cn.windwheel.aps.solve.timefold.TimefoldManageConfigurator;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static cn.windwheel.aps.solve.HybridSchedulingTest.*;

/** End-to-end solver tests use the same Spring-backed time listeners as production. */
class AdaptiveWarmStartTest {
    private final HybridSchedulingTest fixture = new HybridSchedulingTest();
    @org.junit.jupiter.api.BeforeEach void setup() { fixture.setup(); }
    @org.junit.jupiter.api.AfterEach void cleanup() { fixture.cleanup(); }
    @Test
    void coldRollbackKeepsEveryTrialIndependentAndOriginalInputUnchanged() throws Exception {
        var problem = problem(SolverEngineEnum.TIMEFOLD,PlanningDirectionEnum.FORWARD,SolverProfileEnum.DEFAULT);
        problem.getStrategy().getConfig().setMaterialConstrained(true).setAdaptive(new AdaptiveSolveConfig()
                .setEnabled(true).setWarmStartEnabled(false).setBudgetMode(AdaptiveSolveConfig.BudgetMode.FIXED)
                .setPopulationSize(4).setGenerations(1).setTotalSeconds(30).setTrialSeconds(2).setTrialScoreLimit(80));
        var reports = new ArrayList<AdaptiveScheduling.Report>();
        var result = new SchedulingEngineRouter(new SolveManageConfigurator(),new TimefoldManageConfigurator())
                .solve(UUID.randomUUID(),problem,()->false,reports::add);
        assertEquals(0,result.getTimefoldScore().hardScore());
        var end = reports.get(reports.size()-1);
        assertEquals(8,end.trials().size());
        assertTrue(end.trials().stream().allMatch(t -> t.execution().constructionEnabled()
                && t.execution().seedTrial()==-1 && t.execution().scoreLimit()==80));
        assertTrue(problem.getMainResources().get(0).getTaskList().isEmpty());
        assertNull(problem.getTasks().get(0).getResource());
        assertEquals(SolverProfileEnum.DEFAULT, problem.getStrategy().getConfig().getSolverProfile());
    }

    @Test
    void warmSearchWorksBackwardsWithDefaultStrategyAndCancellationAfterWarmTrial() throws Exception {
        var problem = problem(SolverEngineEnum.TIMEFOLD,PlanningDirectionEnum.BACKWARD,SolverProfileEnum.DEFAULT);
        problem.getStrategy().getConfig().setMaterialConstrained(true).setAdaptive(new AdaptiveSolveConfig()
                .setEnabled(true).setPopulationSize(4).setGenerations(1).setTotalSeconds(30)
                .setTrialSeconds(2).setTrialScoreLimit(100));
        new DynamicSchedulingService().apply(problem,new BasicData());
        var reports = new ArrayList<AdaptiveScheduling.Report>();
        var cancel = new java.util.concurrent.atomic.AtomicBoolean();
        var router = new SchedulingEngineRouter(new SolveManageConfigurator(),new TimefoldManageConfigurator());
        assertNull(router.solve(UUID.randomUUID(),problem,cancel::get,report -> {
            reports.add(report);
            if (report.trials().stream().anyMatch(t -> "WARM".equals(t.execution().startMode()))) cancel.set(true);
        }));
        var end = reports.get(reports.size()-1);
        assertEquals("CANCELLED",end.stopReason());
        assertEquals(2,end.trials().size());
        assertEquals("FEASIBLE",end.trials().get(1).status());
        assertNull(problem.getTasks().get(0).getStartTime());
    }

    @Test
    void malformedAssignmentAndFrozenPrefixCannotBeWarmSeeds() throws Exception {
        var p = problem(SolverEngineEnum.TIMEFOLD,PlanningDirectionEnum.FORWARD,SolverProfileEnum.DEFAULT);
        var solved = new SchedulingEngineRouter(new SolveManageConfigurator(),new TimefoldManageConfigurator())
                .solve(UUID.randomUUID(),p);
        assertTrue(AdaptiveScheduling.completeQueues(solved));
        var r = solved.getMainResources().get(0);
        var task = r.getTaskList().get(0);
        r.getTaskList().add(task);
        assertFalse(AdaptiveScheduling.completeQueues(solved));
        r.getTaskList().remove(r.getTaskList().size()-1);
        r.setFirstUnpinnedIndex(1);
        assertFalse(AdaptiveScheduling.completeQueues(solved));
        task.setPinned(true);
        assertTrue(AdaptiveScheduling.completeQueues(solved));
        task.setResource(new MainProductionResource().setId(r.getId()));
        assertFalse(AdaptiveScheduling.completeQueues(solved));
    }

    @Test
    void scaleBudgetsRespectDeadlineAndFrozenAndMergedTasks() {
        var p = problem(SolverEngineEnum.TIMEFOLD,PlanningDirectionEnum.FORWARD,SolverProfileEnum.DEFAULT);
        var config = new AdaptiveSolveConfig().setTotalSeconds(500).setTrialSeconds(20).setTrialScoreLimit(8000);
        p.setTasks(new ArrayList<>());
        for (int i=0;i<144;i++) p.getTasks().add(task("S"+i));
        var large = AdaptiveBudgetPolicy.plan(p,config);
        assertEquals("LARGE",large.tier());
        assertEquals(300000,large.coldMillis());
        assertEquals(48000,large.coldScoreLimit());
        assertEquals(123,large.allocate(false,123,8).millis());
        assertEquals(500,large.allocate(true,4000,8).millis());
        assertEquals(0,large.allocate(true,-5,8).millis());
        p.getTasks().forEach(t -> t.setPinned(true));
        assertEquals("MEDIUM",AdaptiveBudgetPolicy.plan(p,config).tier());
        p.getTasks().subList(0,100).forEach(t -> t.setMergeTaskCode("merged"));
        assertEquals(44,AdaptiveBudgetPolicy.plan(p,config).activeTasks());
        assertEquals("SMALL",AdaptiveBudgetPolicy.plan(p,config).tier());
        config.setBudgetMode(AdaptiveSolveConfig.BudgetMode.FIXED);
        var fixed = AdaptiveBudgetPolicy.plan(p,config);
        assertEquals(20000,fixed.coldMillis());
        assertEquals(8000,fixed.coldScoreLimit());
        assertEquals(4000,fixed.allocate(true,4000,8).millis());
        assertThrows(IllegalArgumentException.class,()->config.setBudgetMode(null).validate());
    }

    @Test
    void warmPhasesNeverContainConstructionAndColdPhasesStillDo() {
        for (var profile : SolverProfileEnum.values()) {
            var c = problem(SolverEngineEnum.TIMEFOLD,PlanningDirectionEnum.FORWARD,profile).getStrategy().getConfig();
            var warm = new TimefoldManageConfigurator().buildConfig(c,true);
            assertEquals(1,warm.getPhaseConfigList().size());
            assertInstanceOf(ai.timefold.solver.core.config.localsearch.LocalSearchPhaseConfig.class,warm.getPhaseConfigList().get(0));
        }
    }
}
