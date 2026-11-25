package cn.windwheel.aps.solve.cpsat;

import cn.windwheel.aps.cpsat.Protocol;
import static cn.windwheel.aps.cpsat.Protocol.*;
import cn.windwheel.aps.entity.dto.planning.CpSatConfig;
import cn.windwheel.aps.solve.model.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

final class CpSatHybridPreparation {
    record Prepared(Request request,boolean initialSeedOnly) {}
    static Prepared prepare(UUID id,SchedulingSolution solution,double seconds,int maxTasks,long deadline,
                            BooleanSupplier stop,Map<String,Object> report) throws Exception {
        var config=solution.getStrategy().getConfig().getCpSat();
        var requestedScope=config.getOptimizationScope();
        Request request;
        if(config.isTimefoldWarmStart())config.setOptimizationScope(CpSatConfig.OptimizationScope.FULL);
        try {request=CpSatProblemMapper.map(id,solution,seconds,maxTasks,stop);}
        finally {config.setOptimizationScope(requestedScope);}
        boolean seedOnly=false;
        if(config.isTimefoldWarmStart()) {
            Map<String,Object> hybrid=new LinkedHashMap<>();report.put("hybrid",hybrid);
            hybrid.put("sharedBudgetSeconds",seconds);hybrid.put("existingBaseline",!request.baseline().isEmpty());
            if(request.baseline().isEmpty()) {
                int percent=config.isSizeAwareBudget()?(request.tasks().size()<=24?30:request.tasks().size()<=48?40:60):config.getTimefoldBudgetPercent();
                long begin=System.nanoTime(),left=Math.max(0,deadline-begin);
                long warmDeadline=begin+left*percent/100;
                hybrid.put("timefoldBudgetPercent",percent);
                var bean=java.lang.management.ManagementFactory.getThreadMXBean();
                long cpuBefore=bean.isCurrentThreadCpuTimeSupported()?bean.getCurrentThreadCpuTime():-1;
                var seed=CpSatHybridSeeder.seed(solution,warmDeadline,stop);
                hybrid.put("timefoldCpuMillis",cpuBefore<0?null:(bean.getCurrentThreadCpuTime()-cpuBefore)/1000000);
                hybrid.put("timefoldMillis",TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-begin));
                if(seed!=null) {
                    Map<String,ProductionTask> byCode=new HashMap<>();seed.getTasks().forEach(t->byCode.put(t.getCode(),t));
                    Map<String,MainProductionResource> resources=new HashMap<>();solution.getMainResources().forEach(r->resources.put(r.getId(),r));
                    for(var t:solution.getTasks()) if(!Boolean.TRUE.equals(t.getPinned())) {
                        var v=byCode.get(t.getCode());
                        if(v!=null && v.getResource()!=null)t.setResource(resources.get(v.getResource().getId())).setStartTime(v.getStartTime());
                    }
                    config.setOptimizationScope(CpSatConfig.OptimizationScope.FULL);
                    try {request=CpSatProblemMapper.map(id,solution,seconds,maxTasks,stop);}
                    finally {config.setOptimizationScope(requestedScope);}
                    seedOnly=!request.baseline().isEmpty();
                    hybrid.put("timefoldScore",seed.getTimefoldScore().toString());
                }
                hybrid.put("seedIndependentlyVerified",seedOnly);
            }
            if(!request.baseline().isEmpty()) {
                config.setOptimizationScope(CpSatConfig.OptimizationScope.LOCAL);
                List<String> local;
                try {local=CpSatNeighborhood.select(request,config);}
                finally {config.setOptimizationScope(requestedScope);}
                // A newly computed seed is not a published plan: do not charge it production disruption costs.
                var optimization=new Optimization(local,seedOnly?0:config.getChangedTaskWeight(),
                        seedOnly?0:config.getResourceChangeWeight(),seedOnly?0:config.getStartShiftMinuteWeight());
                request=new Request(VERSION,request.attempt(),request.parentPid(),request.originEpochMillis(),request.horizon(),
                        request.seconds(),request.workers(),request.seed(),request.weights(),request.resources(),request.tasks(),request.baseline(),optimization);
            } else if(requestedScope==CpSatConfig.OptimizationScope.LOCAL)
                throw new IllegalStateException("Timefold未产生可验证基线，不能执行局部精修");
            hybrid.put("initialSeedOnly",seedOnly);
        }
        double remaining=Math.max(.001,(deadline-System.nanoTime())/1e9);
        request=new Request(VERSION,request.attempt(),request.parentPid(),request.originEpochMillis(),request.horizon(),remaining,
                request.workers(),request.seed(),request.weights(),request.resources(),request.tasks(),request.baseline(),request.optimization(),config.getSearchMode().name());
        return new Prepared(request,seedOnly);
    }
}
