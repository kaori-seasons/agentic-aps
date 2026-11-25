package cn.windwheel.aps.solve.cpsat;

import cn.windwheel.aps.cpsat.Protocol;
import cn.windwheel.aps.cpsat.Protocol.*;
import cn.windwheel.aps.entity.dto.planning.CpSatConfig;
import java.util.*;

/** Exact integer acceptance; a lower raw cost is insufficient when disruption exceeds its benefit. */
public final class CpSatAdoption {
    private CpSatAdoption() {}
    public record Decision(boolean accepted,String reason,long businessGain,long adjustmentCost,long netGain,long changedTasks,
                           long tardinessImprovementMinutes) {}
    public static Decision decide(Request request,List<Assignment> candidate,CpSatConfig config) {
        long cost=Protocol.verify(request,candidate);
        var adjustment=Protocol.adjustment(request,candidate);
        if(request.baseline().isEmpty()) return new Decision(true,"FIRST_FEASIBLE_PLAN",0,0,0,0,0);
        long baselineCost=Protocol.verify(request,request.baseline());
        long gain=Math.subtractExact(baselineCost,cost),net=Math.subtractExact(gain,adjustment.cost());
        long tardy=lateMinutes(request,request.baseline())-lateMinutes(request,candidate);
        String reason="ACCEPTED";
        if(adjustment.changedTasks()==0) reason="UNCHANGED_BASELINE";
        else if(gain<0 || net<config.getMinNetImprovement()) reason="INSUFFICIENT_NET_BENEFIT";
        else if(adjustment.changedTasks()*100L>(long)request.tasks().size()*config.getMaxChangedTaskPercent()) reason="CHANGE_LIMIT_EXCEEDED";
        else if(tardy<config.getMinTardinessImprovementMinutes()) reason="TARDINESS_THRESHOLD_NOT_MET";
        return new Decision("ACCEPTED".equals(reason),reason,gain,adjustment.cost(),net,adjustment.changedTasks(),tardy);
    }
    private static long lateMinutes(Request r,List<Assignment> assignments) {
        Map<String,Long> due=new HashMap<>();r.tasks().forEach(t->{if(t.due()!=null)due.put(t.id(),t.due());});
        return assignments.stream().filter(a->due.containsKey(a.task())).mapToLong(a->Math.max(0,a.end()-due.get(a.task()))/60000).sum();
    }
}
