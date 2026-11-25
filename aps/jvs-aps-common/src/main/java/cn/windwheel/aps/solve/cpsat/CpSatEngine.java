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
        boolean acquired=false; Process process=null; Path dir=null;
        Map<String,Object> report=new LinkedHashMap<>(); long start=System.nanoTime();
        long seconds=solution.getStrategy().getConfig().getSolveTimeSeconds()==null?120:solution.getStrategy().getConfig().getSolveTimeSeconds();
        long deadline=start+TimeUnit.SECONDS.toNanos(seconds+10);
        try {
            acquired=cpu.tryAcquire(settings.getNumWorkers());
            if(!acquired) throw new IllegalStateException("CP-SAT计算线程预算已满");
            BooleanSupplier stop=()->Thread.currentThread().isInterrupted() || cancelled!=null && cancelled.getAsBoolean();
            Request request=CpSatProblemMapper.map(id,solution,seconds,maxTasks,()->stop.getAsBoolean() || System.nanoTime()>deadline);
            byte[] bytes=json.writeValueAsBytes(request);
            if(bytes.length>32*1024*1024) throw new IllegalArgumentException("CP-SAT输入超过32MiB");
            report.put("engine","ORTOOLS_CP_SAT"); report.put("version","9.15.6755"); report.put("modelVersion",VERSION);
            report.put("executionMode",settings.getExecutionMode()); report.put("inputHash",HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)));
            report.put("baselineAccepted",!request.baseline().isEmpty());
            report.put("request",request);
            report.put("baselineCost",request.baseline().isEmpty()?null:Protocol.verify(request,request.baseline()));
            report.put("workers",request.workers()); report.put("seed",request.seed()); report.put("horizonMillis",request.horizon());
            report.put("compileMillis",(System.nanoTime()-start)/1000000);
            dir=Files.createTempDirectory("aps-cpsat-");
            if(Files.getFileStore(dir).supportsFileAttributeView("posix")) Files.setPosixFilePermissions(dir,PosixFilePermissions.fromString("rwx------"));
            Path input=dir.resolve("request.json"),output=dir.resolve("response.json"); Files.write(input,bytes);
            if(stop.getAsBoolean()) throw new CancellationException();
            process=new ProcessBuilder(Path.of(System.getProperty("java.home"),"bin","java").toString(),"-Xmx512m","-jar",jar.toString(),input.toString(),output.toString())
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD).redirectError(ProcessBuilder.Redirect.DISCARD).start();
            while(!process.waitFor(100,TimeUnit.MILLISECONDS)) {
                if(stop.getAsBoolean()) throw new CancellationException();
                if(System.nanoTime()>deadline) throw new IllegalStateException("CP-SAT超出总预算，worker已终止");
            }
            if(stop.getAsBoolean()) throw new CancellationException();
            if(process.exitValue()!=0 || !Files.exists(output)) throw new IllegalStateException("CP-SAT worker异常退出，退出码="+process.exitValue());
            if(Files.size(output)>16L*1024*1024) throw new IllegalStateException("CP-SAT响应超出限制");
            Response response=json.readValue(output.toFile(),Response.class); report.put("worker",response);
            if(response.version()!=VERSION || !id.toString().equals(response.attempt())) throw new IllegalStateException("CP-SAT响应身份不匹配");
            if(response.cost()==null || response.assignments().isEmpty()) throw new IllegalStateException("CP-SAT未返回可行解："+response.status()+" "+response.message());
            long cost=Protocol.verify(request,response.assignments());
            if(cost!=response.cost()) throw new IllegalStateException("CP-SAT结果与独立评分不一致");
            if(!request.baseline().isEmpty() && cost>Protocol.verify(request,request.baseline())) throw new IllegalStateException("CP-SAT候选劣于已验证基线");
            var byCode=new HashMap<String,ProductionTask>(); solution.getTasks().forEach(t->byCode.put(t.getCode(),t));
            var byResource=new HashMap<String,MainProductionResource>(); solution.getMainResources().forEach(r->{byResource.put(r.getId(),r);r.setTaskList(new ArrayList<>());});
            LocalDateTime origin=LocalDateTime.ofInstant(Instant.ofEpochMilli(request.originEpochMillis()),ZoneOffset.UTC);
            for(var a:response.assignments()) {
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
            report.put("verified",true); report.put("businessMetrics",metrics(request,response.assignments()));
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
            if(process!=null && process.isAlive()) {
                process.destroy();
                try { if(!process.waitFor(500,TimeUnit.MILLISECONDS)) process.destroyForcibly(); }
                catch(InterruptedException interrupted) { process.destroyForcibly(); Thread.currentThread().interrupt(); }
            }
            if(dir!=null) try(var paths=Files.walk(dir)) { for(var path:paths.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(path); }
                catch(java.io.IOException ignored) { /* Private OS temp files; cleanup can be retried by the host janitor. */ }
            if(acquired) cpu.release(settings.getNumWorkers()); jobs.release();
            report.put("totalMillis",(System.nanoTime()-start)/1000000); audit.recordCpSat(id,report);
        }
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
