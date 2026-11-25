package cn.windwheel.aps.cpsat;

import java.util.*;
import static cn.windwheel.aps.cpsat.Protocol.*;

/** Sound interval propagation only: due dates and heuristic incumbent makespans are never hard bounds. */
final class TimeDomains {
    record Bounds(long startMin,long startMax,long endMin,long endMax) {}
    record Result(Map<String,Bounds> bounds,boolean feasible,int passes) {}
    static Result propagate(Request r) {
        Map<String,long[]> ds=new LinkedHashMap<>();
        Map<String,Assignment> fixed=new HashMap<>();
        if(r.optimization().movableTasks()!=null) for(var a:r.baseline())
            if(!r.optimization().movableTasks().contains(a.task())) fixed.put(a.task(),a);
        for(var t:r.tasks()) {
            if(t.frozen()!=null) fixed.put(t.id(),t.frozen());
            var a=fixed.get(t.id());
            ds.put(t.id(),a==null?new long[]{Math.max(0,t.release()),r.horizon(),0,r.horizon()}:
                new long[]{Math.max(t.release(),a.start()),a.start(),a.end(),a.end()});
        }
        boolean feasible=true;int pass=0;
        // Early termination only weakens propagation, never removes a legal assignment.
        for(;pass<2*r.tasks().size()+2;pass++) {
            boolean changed=false;
            for(var t:r.tasks()) {
                long[] d=ds.get(t.id());long lo=Long.MAX_VALUE,hi=Long.MIN_VALUE,elo=lo,ehi=hi;
                if(t.frozen()==null) {
                    for(var o:t.options()) {
                        var f=fixed.get(t.id());if(f!=null && !f.resource().equals(o.resource()))continue;
                        for(var seg:o.segments()) {
                            long a=Math.max(Math.max(seg.firstStart(),d[0]),d[2]-seg.elapsed());
                            long b=Math.min(Math.min(seg.lastStart(),d[1]),d[3]-seg.elapsed());
                            if(a<=b) {lo=Math.min(lo,a);hi=Math.max(hi,b);elo=Math.min(elo,a+seg.elapsed());ehi=Math.max(ehi,b+seg.elapsed());}
                        }
                    }
                    if(lo==Long.MAX_VALUE) {feasible=false;break;}
                    changed|=intersect(d,lo,hi,elo,ehi);
                }
                for(var dep:t.dependencies()) {
                    long[] f=ds.get(dep.predecessor());if(f==null)throw new IllegalArgumentException("Missing predecessor");
                    int index=dep.endEnd()?0:2;
                    long lower=Math.addExact(f[index],dep.startLag()),upper=Math.subtractExact(d[1],dep.startLag());
                    if(d[0]<lower){d[0]=lower;changed=true;}
                    if(f[index+1]>upper){f[index+1]=upper;changed=true;}
                    if(dep.endEnd()) {
                        lower=Math.addExact(f[2],dep.endLag());upper=Math.subtractExact(d[3],dep.endLag());
                        if(d[2]<lower){d[2]=lower;changed=true;}
                        if(f[3]>upper){f[3]=upper;changed=true;}
                    }
                }
            }
            if(ds.values().stream().anyMatch(d->d[0]>d[1] || d[2]>d[3]))feasible=false;
            if(!feasible || !changed)break;
        }
        Map<String,Bounds> out=new LinkedHashMap<>();ds.forEach((id,d)->out.put(id,new Bounds(d[0],d[1],d[2],d[3])));
        return new Result(out,feasible,pass+1);
    }
    private static boolean intersect(long[] d,long lo,long hi,long elo,long ehi) {
        long a=Math.max(d[0],lo),b=Math.min(d[1],hi),c=Math.max(d[2],elo),e=Math.min(d[3],ehi);
        boolean change=a!=d[0] || b!=d[1] || c!=d[2] || e!=d[3];d[0]=a;d[1]=b;d[2]=c;d[3]=e;return change;
    }
}
