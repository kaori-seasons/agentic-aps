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
}
