package cn.windwheel.aps.solve.cpsat;

import cn.windwheel.aps.cpsat.Protocol;
import cn.windwheel.aps.entity.enums.*;
import cn.windwheel.aps.enums.DayOfWeekEnum;
import cn.windwheel.aps.solve.model.*;
import cn.windwheel.aps.solve.score.SchedulingRules;
import cn.windwheel.aps.solve.util.*;
import java.time.*;
import java.util.*;
import java.util.function.BooleanSupplier;
import static cn.windwheel.aps.cpsat.Protocol.*;

public final class CpSatProblemMapper {
    private CpSatProblemMapper() {}
    public static void prepare(SchedulingSolution solution,BasicData data) {
        if(solution.getStrategy().getConfig().getSolverEngine()!=SolverEngineEnum.ORTOOLS_CP_SAT) return;
        if(!data.getUnreservedPurchasedMaterials().isEmpty())
            throw new IllegalArgumentException("CP-SAT存在未落实供给的外购物料："+String.join(",",data.getUnreservedPurchasedMaterials()));
        for(var t:solution.getTasks()) {
            var release=data.getReservedMaterialAvailableAt().get(t.getMainOrderId());
            if(release==null && t.getOrder()!=null) release=data.getReservedMaterialAvailableAt().get(t.getOrder().getId());
            if(release!=null && (t.getNotBefore()==null || release.isAfter(t.getNotBefore()))) t.setNotBefore(release);
            if(!Boolean.TRUE.equals(t.getPinned())) data.getBaselineTasks().stream().filter(b->b.getCode().equals(t.getCode()))
                .findFirst().ifPresent(b->solution.getMainResources().stream().filter(r->r.getId().equals(b.getMainResourceId()))
                    .findFirst().ifPresent(r->{
                        t.setResource(r).setStartTime(b.getStartTime());
                        // A changed duration/calendar is not the same confirmed baseline.
                        if(!Objects.equals(t.getEndTime(),b.getEndTime())) t.setStartTime(null);
                    }));
        }
    }

    public static Request map(UUID id,SchedulingSolution s,double seconds,int maxTasks,BooleanSupplier cancelled) {
        var config=s.getStrategy().getConfig(); var settings=config.getCpSat();
        var active=s.getTasks().stream().filter(SchedulingRules::active).sorted(Comparator.comparing(ProductionTask::getCode)).toList();
        if(active.isEmpty() || active.size()>maxTasks) throw new IllegalArgumentException("CP-SAT任务数超出配置上限："+maxTasks);
        LocalDateTime origin=s.getPlanTime().toLocalDate().atStartOfDay();
        for(var t:active) if(Boolean.TRUE.equals(t.getPinned()) && t.getStartTime()!=null && t.getStartTime().isBefore(origin))
            origin=t.getStartTime().toLocalDate().atStartOfDay();
        long horizon=Duration.between(origin,s.getPlanTime().plusDays(settings.getHorizonDays())).toMillis();
        if(horizon>180L*86400000) throw new IllegalArgumentException("冻结历史与计划窗口合计不得超过180天");
        final LocalDateTime zero=origin; final long limit=horizon;
        Map<String,Resource> resources=new LinkedHashMap<>();
        for(var r:s.getMainResources()) {
            List<Window> working=windows(r.getWorkCalendars(),zero,limit);
            var downtime=r.getDowntimes().stream().map(w->new Window(Math.max(0,tick(zero,w.getStart())),Math.min(limit,tick(zero,w.getEnd()))))
                    .filter(w->w.end()>w.start()).toList();
            resources.put(r.getId(),new Resource(r.getId(),working,merge(downtime)));
        }
        List<Task> tasks=new ArrayList<>(); var baseline=new ArrayList<Assignment>();
        Map<String,List<Segment>> cache=new HashMap<>(); int segments=0; long arcs=0;
        for(var t:active) {
            if(cancelled.getAsBoolean()) throw new java.util.concurrent.CancellationException();
            if(t.getMaterial()==null) throw new IllegalArgumentException("工序产物主数据缺失，无法计算统一成本："+t.getCode());
            if(t.getProcess().getUseAuxiliaryResources()!=null && !t.getProcess().getUseAuxiliaryResources().isEmpty())
                throw new IllegalArgumentException("CP-SAT辅资源缺少容量及占用规则，禁止忽略："+t.getCode());
            if(t.getMaterialDelayMap()!=null && t.getMaterialDelayMap().values().stream().anyMatch(d->!d.isZero()))
                throw new IllegalArgumentException("CP-SAT不接受无确定供给的缺料提前期推测，请补充采购到货预约："+t.getCode());
            long pre=duration(t.getProcess().getPreIntervalDuration());
            long release=Boolean.TRUE.equals(t.getPinned())?0:tick(zero,s.getPlanTime())+pre;
            if(t.getNotBefore()!=null) release=Math.max(release,tick(zero,t.getNotBefore()));
            List<Option> options=new ArrayList<>();
            for(var eligible:t.getProcess().getUseMainResources()) {
                Resource resource=resources.get(eligible.getId()); if(resource==null) continue;
                long duration=TaskDurationUtils.calculateTaskDuration(resource.id(),t.getProcess(),t.getQuantity()).toMillis();
                if(duration<=0) throw new IllegalArgumentException("工序时长必须为正毫秒："+t.getCode());
                var pieces=cache.computeIfAbsent(resource.id()+":"+duration,k->Protocol.segments(resource.working(),duration));
                segments+=pieces.size(); if(segments>150000) throw new IllegalArgumentException("CP-SAT日历分段超过150000，请缩小计划窗口");
                options.add(new Option(resource.id(),duration,pieces));
            }
            if(options.isEmpty()) throw new IllegalArgumentException("没有可用候选资源："+t.getCode());
            var deps=new ArrayList<Dependency>();
            if(t.getFrontTasks()!=null) for(var f:t.getFrontTasks()) {
                if(!SchedulingRules.active(f)) throw new IllegalArgumentException("CP-SAT依赖合并子任务尚未规范化："+t.getCode());
                boolean ee=!Boolean.TRUE.equals(t.getStartTask()) && t.getProcess().getProcessRelationship()==ProcessRelationshipEnum.EE;
                long buffer=duration(t.getProcess().getBufferTime());
                deps.add(new Dependency(f.getCode(),ee,pre+(ee?buffer:duration(f.getProcess().getPostIntervalDuration())),ee?buffer:0));
            }
            if(t.getFrontTaskCodes()!=null && t.getFrontTaskCodes().size()!=deps.size())
                throw new IllegalArgumentException("工序依赖不完整："+t.getCode());
            deps.sort(Comparator.comparing(Dependency::predecessor));
            options.sort(Comparator.comparing(Option::resource));
            Assignment frozen=null;
            if(Boolean.TRUE.equals(t.getPinned())) {
                if(t.getFrozenStartTime()==null || t.getFrozenEndTime()==null || t.getFrozenResourceId()==null)
                    throw new IllegalArgumentException("冻结基线不完整："+t.getCode());
                frozen=new Assignment(t.getCode(),t.getFrozenResourceId(),tick(zero,t.getFrozenStartTime()),tick(zero,t.getFrozenEndTime()));
            }
            Long due=Boolean.TRUE.equals(t.getEndTask()) && t.getOrder()!=null && t.getOrder().getDeliveryTime()!=null?tick(zero,t.getOrder().getDeliveryTime()):null;
            tasks.add(new Task(t.getCode(),t.getMaterial().getId(),t.getPriority()==null?Integer.MAX_VALUE:t.getPriority(),release,due,pre,options,deps,frozen));
            if(SchedulingRules.scheduled(t)) baseline.add(new Assignment(t.getCode(),t.getResource().getId(),tick(zero,t.getStartTime()),tick(zero,t.getEndTime())));
        }
        for(var r:resources.values()) {long n=tasks.stream().filter(t->t.options().stream().anyMatch(o->o.resource().equals(r.id()))).count(); arcs+=n*n;}
        if(arcs>100000) throw new IllegalArgumentException("CP-SAT资源排序候选超过100000，请分区求解");
        var w=config.getConstraintWeightConfig();
        var weights=new Weights(w.getPriorityWeight(),w.getFinishEarlyWeight(),w.getMinimizeTardinessWeight(),w.getBalanceResourceLoadWeight(),w.getMinimizeGapBetweenDependentTasksWeight(),w.getMinimizeSetupTimeWeight());
        long epoch=zero.toInstant(ZoneOffset.UTC).toEpochMilli();
        if(epoch<0) throw new IllegalArgumentException("CP-SAT计划时间必须在1970年之后");
        Request request=new Request(VERSION,id.toString(),ProcessHandle.current().pid(),epoch,limit,seconds,settings.getNumWorkers(),
                config.getRandomSeed()==null?0:Math.toIntExact(config.getRandomSeed()),weights,resources.values().stream().sorted(Comparator.comparing(Resource::id)).toList(),tasks,List.of());
        if(settings.isUseBaselineHint()) try {
            Protocol.verify(request,baseline);
            request=new Request(VERSION,request.attempt(),request.parentPid(),epoch,limit,seconds,request.workers(),request.seed(),weights,request.resources(),tasks,baseline);
        } catch(IllegalArgumentException|ArithmeticException ignored) { /* Invalid baselines are never hints or fallback. */ }
        var optimization=new Optimization(CpSatNeighborhood.select(request,settings),settings.getChangedTaskWeight(),
                settings.getResourceChangeWeight(),settings.getStartShiftMinuteWeight());
        return new Request(VERSION,request.attempt(),request.parentPid(),epoch,limit,seconds,request.workers(),request.seed(),
                weights,request.resources(),tasks,request.baseline(),optimization);
    }
    private static long duration(String value) { return DurationUtils.convertDuration(value).toMillis(); }
    static long tick(LocalDateTime origin,LocalDateTime time) {
        if(time.getNano()%1000000!=0) throw new IllegalArgumentException("CP-SAT不允许损失亚毫秒精度");
        return Duration.between(origin,time).toMillis();
    }
    /** Compile only the calendar semantics already supported by the application's time mapper. */
    static List<Window> windows(List<WorkCalendar> calendars,LocalDateTime origin,long horizon) {
        if(calendars==null || calendars.isEmpty()) return List.of(new Window(0,horizon));
        for(var c:calendars) for(var mode:c.getWorkModeTimes()) if(!mode.getStart().isBefore(mode.getEnd()))
            throw new IllegalArgumentException("跨午夜班次请先拆分为日内班次，禁止与现有日历计算产生歧义");
        TreeSet<Long> cuts=new TreeSet<>(List.of(0L,horizon));
        for(var c:calendars) {
            for(var b:List.of(c.getBeginTime(),c.getEndTime().plusNanos(1000000))) { long v=tick(origin,b); if(v>0 && v<horizon) cuts.add(v); }
        }
        for(var day=origin.toLocalDate();!day.atStartOfDay().isAfter(origin.plusNanos(horizon*1000000));day=day.plusDays(1)) {
            long midnight=tick(origin,day.atStartOfDay()); if(midnight>0 && midnight<horizon) cuts.add(midnight);
            for(var c:calendars) for(var mode:c.getWorkModeTimes()) for(var time:List.of(mode.getStart(),mode.getEnd())) {
                long v=tick(origin,day.atTime(time)); if(v>0 && v<horizon) cuts.add(v);
            }
        }
        var values=new ArrayList<>(cuts); var result=new ArrayList<Window>();
        for(int i=0;i<values.size()-1;i++) {
            long a=values.get(i),b=values.get(i+1); var time=origin.plusNanos(a*1000000);
            var covered=calendars.stream().filter(c->!time.isBefore(c.getBeginTime()) && !time.isAfter(c.getEndTime())).toList();
            if(covered.isEmpty()) { result.add(new Window(a,b)); continue; }
            var weekday=DayOfWeekEnum.getDayOfWeekEnumByName(time.getDayOfWeek().name());
            var working=covered.stream().filter(c->c.getWorkDaySetting()!=null && Boolean.TRUE.equals(c.getWorkDaySetting().get(weekday))).toList();
            if(working.isEmpty()) continue;
            var chosen=working.stream().max(Comparator.comparing(WorkCalendar::getPriority)).orElseThrow();
            if(chosen.getWorkModeTimes().stream().anyMatch(m->!time.toLocalTime().isBefore(m.getStart()) && time.toLocalTime().isBefore(m.getEnd())))
                result.add(new Window(a,b));
        }
        return merge(result);
    }
    static List<Window> merge(List<Window> input) {
        var result=new ArrayList<Window>();
        for(var w:input.stream().sorted(Comparator.comparingLong(Window::start)).toList()) {
            if(!result.isEmpty() && w.start()<=result.get(result.size()-1).end()) {
                var last=result.remove(result.size()-1); result.add(new Window(last.start(),Math.max(last.end(),w.end())));
            } else result.add(w);
        }
        return result;
    }
}
