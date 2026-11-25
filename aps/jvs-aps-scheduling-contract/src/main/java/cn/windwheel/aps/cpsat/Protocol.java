package cn.windwheel.aps.cpsat;

import java.util.*;

/** Versioned, engine-neutral process boundary. All times are exact relative milliseconds. */
public final class Protocol {
    private Protocol() {}
    public static final int VERSION = 3;
    public record Window(long start, long end) {}
    public record Segment(long firstStart, long lastStart, long elapsed) {}
    public record Resource(String id, List<Window> working, List<Window> downtime) {}
    public record Option(String resource, long processing, List<Segment> segments) {}
    public record Dependency(String predecessor, boolean endEnd, long startLag, long endLag) {}
    public record Assignment(String task, String resource, long start, long end) {}
    public record Task(String id, String material, int priority, long release, Long due,
                       long resourceGap, List<Option> options, List<Dependency> dependencies, Assignment frozen) {}
    public record Weights(long priority, long finish, long tardiness, long load, long gap, long setup) {}
    public record Request(int version, String attempt, long parentPid, long originEpochMillis, long horizon,
                          double seconds, int workers, int seed, Weights weights, List<Resource> resources,
                          List<Task> tasks, List<Assignment> baseline, Optimization optimization, String searchMode) {
        public Request(int version,String attempt,long parentPid,long originEpochMillis,long horizon,double seconds,int workers,int seed,
                       Weights weights,List<Resource> resources,List<Task> tasks,List<Assignment> baseline,Optimization optimization) {
            this(version,attempt,parentPid,originEpochMillis,horizon,seconds,workers,seed,weights,resources,tasks,baseline,optimization,"AUTOMATIC");
        }
        public Request(int version,String attempt,long parentPid,long originEpochMillis,long horizon,double seconds,int workers,int seed,
                       Weights weights,List<Resource> resources,List<Task> tasks,List<Assignment> baseline) {
            this(version,attempt,parentPid,originEpochMillis,horizon,seconds,workers,seed,weights,resources,tasks,baseline,Optimization.full());
        }
        public Request { if(optimization==null) optimization=Optimization.full(); if(searchMode==null)searchMode="AUTOMATIC"; }
    }
    /** movableTasks is null for full search; an empty list explicitly fixes every task. */
    public record Optimization(List<String> movableTasks,long changedWeight,long resourceWeight,long shiftMinuteWeight) {
        public Optimization {
            if(changedWeight<0 || resourceWeight<0 || shiftMinuteWeight<0 || changedWeight>1000000000L
                    || resourceWeight>1000000000L || shiftMinuteWeight>1000000000L)
                throw new IllegalArgumentException("Invalid adjustment weights");
            if(movableTasks!=null) movableTasks=List.copyOf(movableTasks);
        }
        public static Optimization full() {return new Optimization(null,0,0,0);}
    }
    public record ModelStats(int variables,int constraints,int circuits,int inputSegments,int retainedSegments) {}
    public record Performance(boolean modelCacheHit,long nativeLoadMillis,long modelBuildMillis,Long objectiveCost,long adjustmentCost,ModelStats modelStats,Long processCpuMillis) {
        public Performance(boolean hit,long load,long build,Long objective,long adjustment) {this(hit,load,build,objective,adjustment,null,null);}
    }
    public record Response(int version, String attempt, String status, String message, List<Assignment> assignments,
                           Long cost, double objectiveBound, double solveSeconds, long buildMillis,
                           long branches, long conflicts, boolean baselineUsed, Performance performance) {
        public Response(int version,String attempt,String status,String message,List<Assignment> assignments,Long cost,
                        double objectiveBound,double solveSeconds,long buildMillis,long branches,long conflicts,boolean baselineUsed) {
            this(version,attempt,status,message,assignments,cost,objectiveBound,solveSeconds,buildMillis,branches,conflicts,baselineUsed,null);
        }
    }
    public record Adjustment(long changedTasks,long resourceChanges,long shiftMinutes,long cost) {}
    public static Adjustment adjustment(Request r,List<Assignment> values) {
        Map<String,Assignment> base=new HashMap<>(); r.baseline().forEach(a->base.put(a.task(),a));
        long changed=0,resources=0,minutes=0;
        for(var a:values) {
            var b=base.get(a.task()); if(b==null) continue;
            if(!a.equals(b)) changed++;
            if(!a.resource().equals(b.resource())) resources++;
            long delta=Math.abs(Math.subtractExact(a.start(),b.start()));
            minutes=Math.addExact(minutes,(delta+59999)/60000);
        }
        var o=r.optimization();
        long cost=add(add(add(0,changed,o.changedWeight()),resources,o.resourceWeight()),minutes,o.shiftMinuteWeight());
        return new Adjustment(changed,resources,minutes,cost);
    }
    public static long objective(Request r,List<Assignment> values) {
        return Math.addExact(verify(r,values),adjustment(r,values).cost());
    }

    /** Exact affine inverse of cumulative working time; envelope spans breaks and retains the machine. */
    public static List<Segment> segments(List<Window> windows, long duration) {
        if (duration <= 0) throw new IllegalArgumentException("Processing duration must be positive");
        List<Segment> result = new ArrayList<>();
        long[] prefix = new long[windows.size()+1];
        for (int i=0;i<windows.size();i++) {
            Window w=windows.get(i);
            if(w.start()<0 || w.end()<=w.start() || i>0 && w.start()<windows.get(i-1).end())
                throw new IllegalArgumentException("Invalid working windows");
            prefix[i+1]=Math.addExact(prefix[i],w.end()-w.start());
        }
        for(int i=0;i<windows.size();i++) for(int j=i;j<windows.size();j++) {
            Window a=windows.get(i), b=windows.get(j);
            // At the exact end boundary, completion belongs to the earlier window.
            long lo=Math.max(0,prefix[j]+1-prefix[i]-duration);
            long hi=Math.min(a.end()-a.start()-1,prefix[j+1]-prefix[i]-duration);
            if(lo<=hi) result.add(new Segment(a.start()+lo,a.start()+hi,
                    b.start()-a.start()+prefix[i]+duration-prefix[j]));
            if(prefix[j]>prefix[i]+a.end()-a.start()+duration) break;
        }
        return List.copyOf(result);
    }

    /** Independent acceptance gate; does not use solver variables, scores or shadow listeners. */
    public static long verify(Request r, List<Assignment> assignments) {
        if(r.version()!=VERSION || assignments==null || assignments.size()!=r.tasks().size())
            throw new IllegalArgumentException("Incomplete/version-mismatched solution");
        Map<String,Assignment> byId=new HashMap<>();
        for(var a:assignments) if(byId.put(a.task(),a)!=null) throw new IllegalArgumentException("Duplicate task");
        if(r.optimization().movableTasks()!=null) {
            Set<String> movable=new HashSet<>(r.optimization().movableTasks());
            Set<String> ids=new HashSet<>();r.tasks().forEach(t->ids.add(t.id()));
            if(!ids.containsAll(movable) || movable.size()!=r.optimization().movableTasks().size()
                    || r.baseline().size()!=r.tasks().size()) throw new IllegalArgumentException("Invalid local scope/baseline");
            for(var b:r.baseline()) if(!movable.contains(b.task()) && !b.equals(byId.get(b.task())))
                throw new IllegalArgumentException("Task outside local scope changed: "+b.task());
        }
        Map<String,Resource> resources=new HashMap<>();
        for(var resource:r.resources()) resources.put(resource.id(),resource);
        Map<String,List<Assignment>> queues=new HashMap<>();
        Map<String,Long> loads=new HashMap<>();
        Map<String,Task> tasks=new HashMap<>();
        long cost=0,maxEnd=0;
        for(var t:r.tasks()) {
            if(tasks.put(t.id(),t)!=null) throw new IllegalArgumentException("Duplicate input task");
            Assignment a=byId.get(t.id());
            if(a==null || a.start()<0 || a.end()<=a.start() || a.end()>r.horizon() || a.start()<t.release())
                throw new IllegalArgumentException("Task missing/out of bounds: "+t.id());
            Option o=t.options().stream().filter(x->x.resource().equals(a.resource())).findFirst()
                    .orElseThrow(()->new IllegalArgumentException("Ineligible resource: "+t.id()));
            if(t.frozen()!=null) {
                if(!t.frozen().equals(a)) throw new IllegalArgumentException("Frozen task changed: "+t.id());
            } else if(o.segments().stream().noneMatch(s->a.start()>=s.firstStart() && a.start()<=s.lastStart()
                    && a.end()==a.start()+s.elapsed())) throw new IllegalArgumentException("Calendar mismatch: "+t.id());
            Resource resource=resources.get(a.resource());
            if(resource==null || resource.downtime().stream().anyMatch(w->a.start()<w.end() && w.start()<a.end()))
                throw new IllegalArgumentException("Resource unavailable: "+t.id());
            queues.computeIfAbsent(a.resource(),k->new ArrayList<>()).add(a);
            loads.merge(a.resource(),o.processing()/60000,Math::addExact);
            maxEnd=Math.max(maxEnd,a.end());
            if(t.due()!=null) cost=add(cost,Math.max(0,a.end()-t.due())/60000,r.weights().tardiness());
            long frontEnd=0;
            for(var d:t.dependencies()) {
                var f=byId.get(d.predecessor());
                if(f==null || a.start()<(d.endEnd()?f.start():f.end())+d.startLag()
                        || d.endEnd() && a.end()<f.end()+d.endLag())
                    throw new IllegalArgumentException("Dependency broken: "+t.id());
                frontEnd=Math.max(frontEnd,f.end());
            }
            if(!t.dependencies().isEmpty()) cost=add(cost,Math.max(0,a.start()-frontEnd)/60000,r.weights().gap());
        }
        cost=add(cost,Math.floorDiv(Math.addExact(r.originEpochMillis(),maxEnd),1000),r.weights().finish());
        for(long load:loads.values()) cost=add(cost,Math.multiplyExact(load,load),r.weights().load());
        for(var q:queues.values()) {
            q.sort(Comparator.comparingLong(Assignment::start).thenComparing(Assignment::task));
            for(int i=0;i<q.size();i++) {
                var a=q.get(i); var ta=tasks.get(a.task());
                if(i>0) {
                    var b=q.get(i-1);
                    if(a.start()<b.end() || ta.frozen()==null && a.start()<b.end()+ta.resourceGap())
                        throw new IllegalArgumentException("Resource overlap/gap: "+a.task());
                    if(!Objects.equals(ta.material(),tasks.get(b.task()).material())) cost=add(cost,1,r.weights().setup());
                }
                for(int j=i+1;j<q.size();j++) {
                    Task tb=tasks.get(q.get(j).task());
                    if(ta.frozen()==null && tb.frozen()==null && ta.priority()>tb.priority())
                        cost=add(cost,(long)ta.priority()-tb.priority(),r.weights().priority());
                }
            }
        }
        return cost;
    }
    private static long add(long total,long value,long weight) {
        return Math.addExact(total,Math.multiplyExact(value,weight));
    }
}
