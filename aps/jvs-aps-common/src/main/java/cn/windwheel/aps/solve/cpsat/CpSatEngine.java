package cn.windwheel.aps.solve.cpsat;

import cn.windwheel.aps.cpsat.Protocol;
import cn.windwheel.aps.entity.dto.planning.CpSatConfig;
import cn.windwheel.aps.solve.SolveAuditService;
import cn.windwheel.aps.solve.model.*;
import cn.windwheel.aps.solve.score.SchedulingRules;
import cn.windwheel.common.utils.TenantContextHolder;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.BooleanSupplier;
import static cn.windwheel.aps.cpsat.Protocol.*;

@Component
public class CpSatEngine {
    private final SolveAuditService audit;
    private final org.springframework.jdbc.core.JdbcTemplate jdbc;
    private final ObjectMapper json=new ObjectMapper();
    @Value("${aps.cp-sat.enabled:false}") private boolean enabled;
    @Value("${aps.cp-sat.worker-jar:}") private String workerJar;
    @Value("${aps.cp-sat.candidate-tenants:}") private String candidateTenants;
    @Value("${aps.cp-sat.max-tasks:300}") private int maxTasks;
    // A per-service CPU admission gate; deployments must additionally cap worker containers/process memory.
    private final Semaphore cpu=new Semaphore(8,true);
    private final Semaphore jobs=new Semaphore(2,true);
    private final CpSatWorkerPool pool=new CpSatWorkerPool();
    @javax.annotation.PreDestroy public void close() {pool.close();}
    public CpSatEngine(SolveAuditService audit,org.springframework.jdbc.core.JdbcTemplate jdbc) { this.audit=audit; this.jdbc=jdbc; }

    public SchedulingSolution solve(UUID id,SchedulingSolution solution,BooleanSupplier cancelled) throws InterruptedException {
        if(!enabled) throw new IllegalArgumentException("CP-SAT未启用，请配置独立worker路径与发布白名单");
        var settings=solution.getStrategy().getConfig().getCpSat();
        if(settings.getExecutionMode()==CpSatConfig.ExecutionMode.CANDIDATE
                && Arrays.stream(candidateTenants.split(",")).map(String::trim).noneMatch(TenantContextHolder.getTenantId()::equals))
            throw new IllegalArgumentException("当前租户尚未获准生成CP-SAT候选计划，请先使用影子验算");
        Path jar=Path.of(workerJar);
        if(settings.getExecutionMode()==CpSatConfig.ExecutionMode.CANDIDATE) {
            Integer precise=jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() "
                +"AND table_name IN ('jvs_plan_task','jvs_plan_task_pending','jvs_plan_task_adjust') "
                +"AND column_name IN ('start_time','end_time') AND datetime_precision>=3",Integer.class);
            if(precise==null || precise!=6) throw new IllegalStateException("CP-SAT发布需要先执行20261125_cpsat_time_precision.sql，禁止时间精度丢失");
        }
        if(!jar.isAbsolute() || !Files.isRegularFile(jar)) throw new IllegalArgumentException("CP-SAT worker必须配置可读的绝对jar路径");
        if(!jobs.tryAcquire()) throw new IllegalStateException("CP-SAT并发已满，请稍后重试");
        boolean acquired=false;
        Map<String,Object> report=new LinkedHashMap<>(); long start=System.nanoTime();
        long seconds=solution.getStrategy().getConfig().getSolveTimeSeconds()==null?120:solution.getStrategy().getConfig().getSolveTimeSeconds();
        long searchDeadline=start+TimeUnit.SECONDS.toNanos(seconds);
        long deadline=searchDeadline+TimeUnit.SECONDS.toNanos(1);
        try {
            acquired=cpu.tryAcquire(settings.getNumWorkers());
            if(!acquired) throw new IllegalStateException("CP-SAT计算线程预算已满");
            BooleanSupplier stop=()->Thread.currentThread().isInterrupted() || cancelled!=null && cancelled.getAsBoolean();
            var prepared=CpSatHybridPreparation.prepare(id,solution,seconds,maxTasks,searchDeadline,stop,report);
            Request request=prepared.request();
            byte[] bytes=json.writeValueAsBytes(request);
            if(bytes.length>32*1024*1024) throw new IllegalArgumentException("CP-SAT输入超过32MiB");
            report.put("engine","ORTOOLS_CP_SAT"); report.put("version","9.15.6755"); report.put("modelVersion",VERSION);
            report.put("executionMode",settings.getExecutionMode()); report.put("inputHash",HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)));
            report.put("baselineAccepted",!request.baseline().isEmpty());
            report.put("request",request);
            report.put("baselineCost",request.baseline().isEmpty()?null:Protocol.verify(request,request.baseline()));
            report.put("workers",request.workers()); report.put("seed",request.seed()); report.put("horizonMillis",request.horizon());
            report.put("compileMillis",(System.nanoTime()-start)/1000000);
            report.put("optimizationScope",request.optimization().movableTasks()==null?"FULL":"LOCAL");
            report.put("searchMode",request.searchMode());
            report.put("movableTasks",request.optimization().movableTasks()==null?request.tasks().size():request.optimization().movableTasks().size());
            if(stop.getAsBoolean()) throw new CancellationException();
            report.put("workerCpuComplete",true);
            Response response;
            try {
                if(request.seconds()<.1)throw new TimeoutException("Shared search budget exhausted");
                var execution=pool.execute(jar,TenantContextHolder.getTenantId(),request,bytes,settings.isReuseWorker(),deadline,stop);
                response=execution.response();
                report.put("workerReused",execution.reused());report.put("workerPid",execution.pid());
                report.put("workerCpuMillis",response.performance()==null?null:response.performance().processCpuMillis());
                report.put("workerProcessCpuMillis",execution.cpuMillis());
                report.put("workerCompletedJobs",execution.completedJobs());report.put("processStartMillis",execution.processStartMillis());
            } catch(TimeoutException timeout) {
                report.put("workerCpuComplete",false);
                if(stop.getAsBoolean())throw new CancellationException();
                if(request.baseline().isEmpty())throw timeout;
                long fallback=Protocol.verify(request,request.baseline());
                response=new Response(VERSION,request.attempt(),"UNKNOWN","SHARED_BUDGET_EXHAUSTED",request.baseline(),fallback,0,0,0,0,0,true,
                    new Performance(false,0,0,fallback,0));
            }
            // Expand only within the original shared deadline. A small local optimum is not a global optimum.
            var rounds=new ArrayList<Map<String,Object>>();
            if(settings.isTimefoldWarmStart() && !request.baseline().isEmpty() && response.cost()!=null) {
                rounds.add(round(request,response));
                for(int depth=settings.getNeighborhoodDepth()+1;depth<=5;depth++) {
                    if((searchDeadline-System.nanoTime())/1e9<1)break;
                    if(stop.getAsBoolean())throw new CancellationException();
                    var scope=settings.getOptimizationScope();int previousDepth=settings.getNeighborhoodDepth();
                    List<String> movable;
                    try {
                        settings.setOptimizationScope(CpSatConfig.OptimizationScope.LOCAL).setNeighborhoodDepth(depth);
                        movable=CpSatNeighborhood.select(request,settings);
                    } catch(IllegalArgumentException limit) {
                        if(limit.getMessage()!=null && limit.getMessage().contains("超出任务上限"))break;
                        throw limit;
                    } finally {settings.setOptimizationScope(scope).setNeighborhoodDepth(previousDepth);}
                    // Use a monotone union: previous best must remain feasible in every expanded scope.
                    var union=new TreeSet<String>(request.optimization().movableTasks());union.addAll(movable);
                    if(union.size()>settings.getMaxNeighborhoodTasks())break;
                    if(union.size()==request.optimization().movableTasks().size())continue;
                    var o=request.optimization();
                    var expanded=new Request(VERSION,request.attempt(),request.parentPid(),request.originEpochMillis(),request.horizon(),
                        Math.max(.001,(searchDeadline-System.nanoTime())/1e9),request.workers(),request.seed(),request.weights(),request.resources(),
                        request.tasks(),request.baseline(),new Optimization(List.copyOf(union),o.changedWeight(),o.resourceWeight(),o.shiftMinuteWeight()),request.searchMode());
                    try {
                        var execution=pool.execute(jar,TenantContextHolder.getTenantId(),expanded,json.writeValueAsBytes(expanded),settings.isReuseWorker(),deadline,stop);
                        var next=execution.response();
                        if(next.cost()==null || next.performance()==null)throw new IllegalStateException("扩展邻域未返回可校验结果："+next.status());
                        if(Protocol.verify(expanded,next.assignments())!=next.cost()
                            || !Objects.equals(next.performance().objectiveCost(),Protocol.objective(expanded,next.assignments())))
                            throw new IllegalStateException("扩展邻域评分不一致");
                        long previousObjective=Protocol.objective(expanded,response.assignments());
                        rounds.add(round(expanded,next));
                        var cpuMillis=next.performance().processCpuMillis();
                        if(cpuMillis!=null)report.put("workerCpuMillis",(report.get("workerCpuMillis") instanceof Number n?n.longValue():0L)+cpuMillis);
                        if(previousObjective<next.performance().objectiveCost()) {
                            var perf=next.performance();
                            next=new Response(VERSION,next.attempt(),"FEASIBLE","BEST_OF_BOUNDED_NEIGHBORHOODS",response.assignments(),response.cost(),
                                next.objectiveBound(),next.solveSeconds(),next.buildMillis(),next.branches(),next.conflicts(),response.baselineUsed(),
                                new Performance(perf.modelCacheHit(),perf.nativeLoadMillis(),perf.modelBuildMillis(),previousObjective,
                                    Protocol.adjustment(expanded,response.assignments()).cost(),perf.modelStats(),perf.processCpuMillis()));
                        }
                        request=expanded;response=next;
                    } catch(TimeoutException timeout) {
                        if(stop.getAsBoolean())throw new CancellationException();
                        report.put("refinementBudgetExhausted",true);report.put("workerCpuComplete",false);break;
                    }
                }
                report.put("refinementRounds",rounds);
                report.put("request",request);
                report.put("inputHash",HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(json.writeValueAsBytes(request))));
                report.put("movableTasks",request.optimization().movableTasks().size());
            }
            report.put("worker",response);
            if(response.cost()==null || response.assignments().isEmpty()) throw new IllegalStateException("CP-SAT未返回可行解："+response.status()+" "+response.message());
            long cost=Protocol.verify(request,response.assignments());
            if(cost!=response.cost()) throw new IllegalStateException("CP-SAT结果与独立评分不一致");
            if(!request.baseline().isEmpty() && cost>Protocol.verify(request,request.baseline())) throw new IllegalStateException("CP-SAT候选劣于已验证基线");
            if(response.performance()==null || response.performance().objectiveCost()==null
                    || response.performance().objectiveCost()!=Protocol.objective(request,response.assignments()))
                throw new IllegalStateException("CP-SAT调整成本与独立评分不一致");
            var decision=prepared.initialSeedOnly()
                ?new CpSatAdoption.Decision(true,"FIRST_FEASIBLE_PLAN",0,0,0,0,0)
                :CpSatAdoption.decide(request,response.assignments(),settings);
            report.put("adoption",decision);
            // Preserve long precision for browser display; numeric values remain for API consumers.
            report.put("adoptionExact",Map.of("businessGain",Long.toString(decision.businessGain()),
                    "adjustmentCost",Long.toString(decision.adjustmentCost()),"netGain",Long.toString(decision.netGain())));
            report.put("candidateMetrics",metrics(request,response.assignments()));
            var selected=decision.accepted()?response.assignments():request.baseline();
            cost=Protocol.verify(request,selected);
            solution.setCpSatBaselineRetained(!decision.accepted());
            var byCode=new HashMap<String,ProductionTask>(); solution.getTasks().forEach(t->byCode.put(t.getCode(),t));
            var byResource=new HashMap<String,MainProductionResource>(); solution.getMainResources().forEach(r->{byResource.put(r.getId(),r);r.setTaskList(new ArrayList<>());});
            LocalDateTime origin=LocalDateTime.ofInstant(Instant.ofEpochMilli(request.originEpochMillis()),ZoneOffset.UTC);
            for(var a:selected) {
                var t=byCode.get(a.task()); var resource=byResource.get(a.resource());
                t.setResource(resource).setStartTime(origin.plusNanos(a.start()*1000000)); resource.getTaskList().add(t);
                // Check against the actual application calendar implementation, not just the compiled calendar.
                if(!origin.plusNanos(a.end()*1000000).equals(t.getEndTime()) || SchedulingRules.unavailable(t) || SchedulingRules.frozenChanged(t))
                    throw new IllegalStateException("应用时间口径复核失败："+t.getCode());
            }
            for(var t:byCode.values()) if(SchedulingRules.active(t) && t.getFrontTasks()!=null)
                for(var f:t.getFrontTasks()) if(SchedulingRules.dependencyBroken(t,f)) throw new IllegalStateException("应用工序口径复核失败："+t.getCode());
            solution.getMainResources().forEach(r->r.getTaskList().sort(Comparator.comparing(ProductionTask::getStartTime)));
            solution.setScore(org.optaplanner.core.api.score.buildin.hardsoftlong.HardSoftLongScore.of(0,Math.negateExact(cost)));
            solution.setTimefoldScore(ai.timefold.solver.core.api.score.buildin.hardsoftlong.HardSoftLongScore.of(0,Math.negateExact(cost)));
            report.put("verified",true); report.put("businessMetrics",metrics(request,selected));
            if(!request.baseline().isEmpty()) report.put("baselineMetrics",metrics(request,request.baseline()));
            if(stop.getAsBoolean()) throw new CancellationException();
            return solution;
        } catch(CancellationException exception) {
            report.put("status","CANCELLED"); return null;
        } catch(InterruptedException exception) {
            report.put("status","INTERRUPTED"); throw exception;
        } catch(Exception exception) {
            report.put("status","FAILED"); report.put("message",String.valueOf(exception.getMessage()));
            throw new IllegalStateException(exception.getMessage(),exception);
        } finally {
            if(acquired) cpu.release(settings.getNumWorkers()); jobs.release();
            report.put("totalMillis",(System.nanoTime()-start)/1000000); audit.recordCpSat(id,report);
        }
    }
    private static Map<String,Object> round(Request request,Response response) {
        Map<String,Object> round=new LinkedHashMap<>();round.put("movableTasks",request.optimization().movableTasks().size());
        round.put("status",response.status());round.put("cost",response.cost());round.put("performance",response.performance());
        round.put("solveSeconds",response.solveSeconds());return round;
    }
    static Map<String,Object> metrics(Request r,List<Assignment> values) {
        var tasks=new HashMap<String,Task>(); r.tasks().forEach(t->tasks.put(t.id(),t));
        long late=0,minutes=0,makespan=0,changed=0;
        var baseline=new HashMap<String,Assignment>(); r.baseline().forEach(a->baseline.put(a.task(),a));
        for(var a:values) {
            var t=tasks.get(a.task()); if(t.due()!=null && a.end()>t.due()) {late++;minutes+=Math.max(0,a.end()-t.due())/60000;}
            makespan=Math.max(makespan,a.end()); if(baseline.containsKey(a.task()) && !a.equals(baseline.get(a.task()))) changed++;
        }
        return Map.of("lateTerminalTasks",late,"tardinessMinutes",minutes,"makespanMillis",makespan,"changedTasks",changed);
    }
}
