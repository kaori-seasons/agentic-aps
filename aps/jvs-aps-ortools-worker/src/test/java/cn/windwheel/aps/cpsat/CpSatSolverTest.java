package cn.windwheel.aps.cpsat;

import org.junit.jupiter.api.Test;
import java.util.*;
import static cn.windwheel.aps.cpsat.Protocol.*;
import static org.junit.jupiter.api.Assertions.*;

class CpSatSolverTest {
    static final long MIN=60000;
    static Resource resource(String id,List<Window> work,List<Window> down) {return new Resource(id,work,down);}
    static Task task(String id,int priority,long duration,List<Resource> rs,List<Dependency> ds,Assignment frozen) {
        return new Task(id,id,priority,0,5*MIN,0,rs.stream().map(r->new Option(r.id(),duration,Protocol.segments(r.working(),duration))).toList(),ds,frozen);
    }
    static Request request(List<Resource> resources,List<Task> tasks,List<Assignment> baseline) {
        return new Request(VERSION,UUID.randomUUID().toString(),ProcessHandle.current().pid(),1800000000000L,60*MIN,5,1,17,
                new Weights(50,1,2,1,3,4),resources,tasks,baseline);
    }
    @Test void solvesFlexibleMachineCalendarBreakAndAllSixObjectives() {
        Resource a=resource("A",List.of(new Window(0,2*MIN),new Window(5*MIN,60*MIN)),List.of());
        Resource b=resource("B",List.of(new Window(0,60*MIN)),List.of(new Window(0,4*MIN)));
        Task t1=task("one",2,3*MIN,List.of(a,b),List.of(),null);
        Task t2=task("two",1,2*MIN,List.of(a,b),List.of(new Dependency("one",false,MIN,0)),null);
        Request r=request(List.of(a,b),List.of(t1,t2),List.of());
        Response result=new CpSatSolver().solve(r);
        assertEquals("OPTIMAL",result.status()); assertEquals(2,result.assignments().size());
        assertEquals(Protocol.verify(r,result.assignments()),result.cost());
        assertEquals((double)result.cost(),result.objectiveBound(),.001);
    }
    @Test void freezesTaskAndPreservesValidatedBaseline() {
        Resource a=resource("A",List.of(new Window(0,60*MIN)),List.of());
        Assignment frozen=new Assignment("one","A",10*MIN,12*MIN);
        Task t1=task("one",2,2*MIN,List.of(a),List.of(),frozen);
        Task t2=task("two",1,3*MIN,List.of(a),List.of(new Dependency("one",true,MIN,MIN)),null);
        List<Assignment> baseline=List.of(frozen,new Assignment("two","A",13*MIN,16*MIN));
        Request r=request(List.of(a),List.of(t1,t2),baseline);
        Response result=new CpSatSolver().solve(r);
        assertEquals("OPTIMAL",result.status()); assertTrue(result.cost()<=Protocol.verify(r,baseline));
        assertTrue(result.assignments().contains(frozen));
    }
    @Test void conflictingFrozenDowntimeIsInfeasibleAndNeverFallback() {
        Resource a=resource("A",List.of(new Window(0,60*MIN)),List.of(new Window(0,5*MIN)));
        var frozen=new Assignment("one","A",0,2*MIN);
        Request r=request(List.of(a),List.of(task("one",1,2*MIN,List.of(a),List.of(),frozen)),List.of());
        Response result=new CpSatSolver().solve(r);
        assertEquals("INFEASIBLE",result.status()); assertNull(result.cost()); assertTrue(result.assignments().isEmpty());
        assertThrows(IllegalArgumentException.class,()->Protocol.verify(r,List.of(frozen)));
    }
    @Test void refusesForgedMissingDuplicateCalendarAndDependencyResults() {
        Resource a=resource("A",List.of(new Window(0,MIN),new Window(3*MIN,60*MIN)),List.of());
        var tasks=List.of(task("one",1,2*MIN,List.of(a),List.of(),null),task("two",2,MIN,List.of(a),List.of(new Dependency("one",false,0,0)),null));
        Request r=request(List.of(a),tasks,List.of());
        var valid=List.of(new Assignment("one","A",0,4*MIN),new Assignment("two","A",4*MIN,5*MIN));
        assertDoesNotThrow(()->Protocol.verify(r,valid));
        assertThrows(IllegalArgumentException.class,()->Protocol.verify(r,valid.subList(0,1)));
        assertThrows(IllegalArgumentException.class,()->Protocol.verify(r,List.of(valid.get(0),valid.get(0))));
        assertThrows(IllegalArgumentException.class,()->Protocol.verify(r,List.of(new Assignment("one","A",0,2*MIN),valid.get(1))));
        assertThrows(IllegalArgumentException.class,()->Protocol.verify(r,List.of(valid.get(0),new Assignment("two","A",0,MIN))));
    }

    static Request options(Request r,Optimization o,int seed) {
        return new Request(VERSION,UUID.randomUUID().toString(),r.parentPid(),r.originEpochMillis(),r.horizon(),5,1,seed,r.weights(),r.resources(),r.tasks(),r.baseline(),o);
    }
    @Test void cachesExactModelAcrossSeedsButInvalidatesOnConstraintOrWeightChange() throws Exception {
        Resource a=resource("A",List.of(new Window(0,60*MIN)),List.of());
        Request r=request(List.of(a),List.of(task("one",1,MIN,List.of(a),List.of(),null)),List.of());
        WorkerModelCache cache=new WorkerModelCache();
        Response first=cache.solve(r),second=cache.solve(options(r,Optimization.full(),29));
        assertFalse(first.performance().modelCacheHit());assertTrue(second.performance().modelCacheHit());
        assertNotEquals(first.attempt(),second.attempt());assertEquals(Protocol.objective(r,second.assignments()),second.performance().objectiveCost());
        var tuned=new Request(VERSION,UUID.randomUUID().toString(),r.parentPid(),r.originEpochMillis(),r.horizon(),5,2,43,
            r.weights(),r.resources(),r.tasks(),r.baseline(),r.optimization(),"PORTFOLIO");
        var portfolio=cache.solve(tuned);
        assertTrue(portfolio.performance().modelCacheHit());assertEquals(first.cost(),portfolio.cost());
        assertEquals(Protocol.objective(tuned,portfolio.assignments()),portfolio.performance().objectiveCost());
        Request changed=options(r,new Optimization(null,10,0,0),29);
        assertFalse(cache.solve(changed).performance().modelCacheHit());
        Task old=r.tasks().get(0);
        var later=new Task(old.id(),old.material(),old.priority(),10*MIN,old.due(),old.resourceGap(),old.options(),old.dependencies(),null);
        var delayed=new Request(VERSION,UUID.randomUUID().toString(),r.parentPid(),r.originEpochMillis(),r.horizon(),5,1,17,r.weights(),r.resources(),List.of(later),List.of());
        Response result=cache.solve(delayed);assertFalse(result.performance().modelCacheHit());assertTrue(result.assignments().get(0).start()>=10*MIN);
    }
    @Test void adjustmentCostMakesSmallReshufflesUnattractiveAndMatchesIndependentObjective() {
        Resource a=resource("A",List.of(new Window(0,60*MIN)),List.of());
        var base=List.of(new Assignment("one","A",10*MIN,11*MIN));
        Request r=options(request(List.of(a),List.of(task("one",1,MIN,List.of(a),List.of(),null)),base),new Optimization(null,1000000,3000,1),17);
        Response result=new CpSatSolver().solve(r);
        assertEquals(base,result.assignments());assertEquals(0,result.performance().adjustmentCost());
        assertEquals(Protocol.objective(r,result.assignments()),result.performance().objectiveCost());
        var shifted=List.of(new Assignment("one","A",10*MIN-1,11*MIN-1));
        assertEquals(1,Protocol.adjustment(r,shifted).shiftMinutes());
        assertEquals(1000001,Protocol.adjustment(r,shifted).cost());
    }
    @Test void localScopeFixesExteriorWithoutChangingGlobalObjectiveSemantics() {
        Resource a=resource("A",List.of(new Window(0,60*MIN)),List.of());
        var base=List.of(new Assignment("one","A",10*MIN,11*MIN),new Assignment("two","A",20*MIN,21*MIN));
        Request r=options(request(List.of(a),List.of(task("one",1,MIN,List.of(a),List.of(),null),task("two",2,MIN,List.of(a),List.of(),null)),base),new Optimization(List.of("two"),0,0,0),17);
        Response result=new CpSatSolver().solve(r);
        assertTrue(result.assignments().contains(base.get(0)));
        assertEquals(Protocol.objective(r,result.assignments()),result.performance().objectiveCost());
        assertThrows(IllegalArgumentException.class,()->Protocol.verify(r,List.of(new Assignment("one","A",0,MIN),base.get(1))));
    }

    @Test void prunedAndOriginalModelsHaveIdenticalOptimalCostsWithAndWithoutAdjacencyRules() {
        Resource a=resource("A",List.of(new Window(0,2*MIN),new Window(4*MIN,60*MIN)),List.of());
        for(long setup:List.of(0L,7L)) for(long gap:List.of(0L,MIN)) {
            var one=task("one",2,2*MIN,List.of(a),List.of(),null);
            var original=task("two",1,MIN,List.of(a),List.of(),null);
            var two=new Task(original.id(),original.material(),original.priority(),original.release(),original.due(),gap,original.options(),original.dependencies(),null);
            var basic=request(List.of(a),List.of(one,two),List.of());
            var r=new Request(VERSION,basic.attempt(),basic.parentPid(),basic.originEpochMillis(),basic.horizon(),5,1,17,
                    new Weights(50,1,2,1,3,setup),basic.resources(),basic.tasks(),List.of());
            var before=new CpSatSolver(false,false).solve(r);var after=new CpSatSolver().solve(r);
            assertEquals("OPTIMAL",before.status());assertEquals("OPTIMAL",after.status());assertEquals(before.cost(),after.cost());
            assertEquals(setup==0 && gap==0?0:1,after.performance().modelStats().circuits());
            assertEquals(Protocol.verify(r,after.assignments()),after.cost());
            if(setup==0 && gap==0)assertTrue(after.performance().modelStats().variables()<before.performance().modelStats().variables());
            assertTrue(after.performance().modelStats().retainedSegments()<=before.performance().modelStats().retainedSegments());
        }
    }
    @Test void temporalPropagationNeverExcludesEnumeratedFeasibleAssignmentsIncludingEeAndBreaks() {
        var random=new Random(17);
        for(int trial=0;trial<30;trial++) {
            Resource a=resource("A",List.of(new Window(0,3),new Window(5,10)),List.of());
            var d1=new Option("A",1+random.nextInt(2),List.of());
            var o1=new Option("A",d1.processing(),Protocol.segments(a.working(),d1.processing()));
            var o2=new Option("A",2,Protocol.segments(a.working(),2));
            boolean ee=random.nextBoolean();
            var t1=new Task("a","m",1,random.nextInt(2),null,0,List.of(o1),List.of(),null);
            var t2=new Task("b","m",2,0,null,0,List.of(o2),List.of(new Dependency("a",ee,random.nextInt(2),ee?1:0)),null);
            var r=new Request(VERSION,"enumerate",1,1800000000000L,10,5,1,17,new Weights(0,1,0,0,0,0),List.of(a),List.of(t1,t2),List.of());
            var domains=TimeDomains.propagate(r);int valid=0;
            for(int sa=0;sa<10;sa++)for(int ea=sa+1;ea<=10;ea++)for(int sb=0;sb<10;sb++)for(int eb=sb+1;eb<=10;eb++) {
                var values=List.of(new Assignment("a","A",sa,ea),new Assignment("b","A",sb,eb));
                try {Protocol.verify(r,values);}catch(IllegalArgumentException ex){continue;}
                valid++;assertTrue(domains.feasible());
                for(var value:values) {var b=domains.bounds().get(value.task());
                    assertTrue(value.start()>=b.startMin() && value.start()<=b.startMax() && value.end()>=b.endMin() && value.end()<=b.endMax());}
            }
            assertTrue(valid>0);
        }
    }
}
