package cn.windwheel.aps.solve.cpsat;

import cn.windwheel.aps.entity.dto.planning.CpSatConfig;
import cn.windwheel.aps.cpsat.Protocol;
import static cn.windwheel.aps.cpsat.Protocol.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class CpSatOptimizationTest {
    Request request() {
        var resource=new Resource("r",List.of(new Window(0,3600000)),List.of());
        var option=new Option("r",60000,Protocol.segments(resource.working(),60000));
        return new Request(VERSION,"test",1,1800000000000L,3600000,10,1,17,new Weights(0,1,1,0,0,0),List.of(resource),
                List.of(new Task("a","m",1,0,0L,0,List.of(option),List.of(),null),
                        new Task("b","m",1,0,0L,0,List.of(option),List.of(new Dependency("a",false,0,0)),null),
                        new Task("c","m",1,0,0L,0,List.of(option),List.of(),null)),
                List.of(new Assignment("a","r",600000,660000),new Assignment("b","r",1200000,1260000),new Assignment("c","r",1800000,1860000)),
                new Optimization(null,10,20,1));
    }
    List<Assignment> candidate() {return List.of(new Assignment("a","r",0,60000),new Assignment("b","r",60000,120000),new Assignment("c","r",120000,180000));}
    @Test void requiresNetBenefitAndHonorsChangeAndTardinessGates() {
        var r=request();var c=new CpSatConfig();
        assertTrue(CpSatAdoption.decide(r,candidate(),c).accepted());
        c.setMinNetImprovement(1000000);assertEquals("INSUFFICIENT_NET_BENEFIT",CpSatAdoption.decide(r,candidate(),c).reason());
        c.setMinNetImprovement(1).setMaxChangedTaskPercent(50);assertEquals("CHANGE_LIMIT_EXCEEDED",CpSatAdoption.decide(r,candidate(),c).reason());
        c.setMaxChangedTaskPercent(100).setMinTardinessImprovementMinutes(10000);
        assertEquals("TARDINESS_THRESHOLD_NOT_MET",CpSatAdoption.decide(r,candidate(),c).reason());
        assertEquals("UNCHANGED_BASELINE",CpSatAdoption.decide(r,r.baseline(),new CpSatConfig()).reason());
    }
    @Test void neighborhoodExpandsDependenciesAndImmediateResourceNeighborsDeterministically() {
        var c=new CpSatConfig().setOptimizationScope(CpSatConfig.OptimizationScope.LOCAL).setFocusTaskCodes(List.of("a")).setNeighborhoodDepth(1);
        assertEquals(List.of("a","b"),CpSatNeighborhood.select(request(),c));
        c.setMaxNeighborhoodTasks(1);assertThrows(IllegalArgumentException.class,()->CpSatNeighborhood.select(request(),c));
        c.setFocusTaskCodes(List.of("missing"));assertThrows(IllegalArgumentException.class,()->CpSatNeighborhood.select(request(),c));
    }
    @Test void autoScopeUsesWorstLateTaskAndConfigFailsClosed() {
        var c=new CpSatConfig().setOptimizationScope(CpSatConfig.OptimizationScope.LOCAL).setNeighborhoodDepth(0);
        assertEquals(List.of("c"),CpSatNeighborhood.select(request(),c));
        c.setUseBaselineHint(false);assertThrows(IllegalArgumentException.class,c::validate);
        c.setUseBaselineHint(true).setChangedTaskWeight(-1);assertThrows(IllegalArgumentException.class,c::validate);
    }
}
