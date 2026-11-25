package cn.windwheel.aps.solve.cpsat;

import cn.windwheel.aps.cpsat.Protocol.*;
import cn.windwheel.aps.entity.dto.planning.CpSatConfig;
import java.util.*;

/** Bounded dependency and resource-adjacency neighborhood; all exterior assignments remain explicit constraints. */
public final class CpSatNeighborhood {
    private CpSatNeighborhood() {}
    public static List<String> select(Request r,CpSatConfig config) {
        if(config.getOptimizationScope()==CpSatConfig.OptimizationScope.FULL) return null;
        if(r.baseline().size()!=r.tasks().size()) throw new IllegalArgumentException("局部优化需要完整且有效的已确认基线，请先全量求解");
        Map<String,Task> tasks=new TreeMap<>();r.tasks().forEach(t->tasks.put(t.id(),t));
        Map<String,Assignment> base=new HashMap<>();r.baseline().forEach(a->base.put(a.task(),a));
        Set<String> selected=new TreeSet<>(config.getFocusTaskCodes());
        if(!tasks.keySet().containsAll(selected)) throw new IllegalArgumentException("局部优化任务编码不存在");
        if(selected.isEmpty()) {
            // Start from one worst late terminal, not every late order in the plant.
            tasks.values().stream().filter(t->t.frozen()==null && t.due()!=null && base.get(t.id()).end()>t.due())
                    .sorted(Comparator.<Task>comparingLong(t->base.get(t.id()).end()-t.due()).reversed().thenComparing(Task::id))
                    .findFirst().ifPresent(t->selected.add(t.id()));
        }
        if(selected.isEmpty()) {
            // On-time plans still have a makespan bottleneck; avoid an empty refinement model.
            tasks.values().stream().filter(t->t.frozen()==null)
                .sorted(Comparator.<Task>comparingLong(t->base.get(t.id()).end()).reversed().thenComparing(Task::id))
                .findFirst().ifPresent(t->selected.add(t.id()));
        }
        Map<String,Set<String>> edges=new HashMap<>();tasks.keySet().forEach(id->edges.put(id,new TreeSet<>()));
        for(var t:tasks.values()) for(var d:t.dependencies()) link(edges,t.id(),d.predecessor());
        Map<String,List<Assignment>> queues=new TreeMap<>();
        r.baseline().forEach(a->queues.computeIfAbsent(a.resource(),k->new ArrayList<>()).add(a));
        for(var q:queues.values()) {
            q.sort(Comparator.comparingLong(Assignment::start).thenComparing(Assignment::task));
            for(int i=1;i<q.size();i++) link(edges,q.get(i-1).task(),q.get(i).task());
        }
        for(int depth=0;depth<config.getNeighborhoodDepth();depth++) {
            var expanded=new TreeSet<>(selected);selected.forEach(id->expanded.addAll(edges.get(id)));selected.clear();selected.addAll(expanded);
        }
        selected.removeIf(id->tasks.get(id).frozen()!=null);
        if(selected.size()>config.getMaxNeighborhoodTasks()) throw new IllegalArgumentException("局部邻域超出任务上限，请缩小种子或邻接深度");
        return List.copyOf(selected);
    }
    private static void link(Map<String,Set<String>> edges,String a,String b) {edges.get(a).add(b);edges.get(b).add(a);}
}
