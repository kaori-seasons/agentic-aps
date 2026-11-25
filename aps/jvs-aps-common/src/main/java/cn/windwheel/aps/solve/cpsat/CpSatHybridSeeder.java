package cn.windwheel.aps.solve.cpsat;

import cn.windwheel.aps.solve.SolveProblemSnapshot;
import cn.windwheel.aps.solve.model.SchedulingSolution;
import cn.windwheel.aps.solve.timefold.TimefoldManageConfigurator;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.concurrent.*;
import java.util.function.BooleanSupplier;

/** Isolated Timefold search within a single shared wall-clock deadline; no tenant-manager replacement. */
final class CpSatHybridSeeder {
    static SchedulingSolution seed(SchedulingSolution source,long deadline,BooleanSupplier stop) throws Exception {
        var mapper=SolveProblemSnapshot.mapper(new ObjectMapper().findAndRegisterModules());
        var copy=mapper.readValue(mapper.writeValueAsBytes(SolveProblemSnapshot.from(source)),SolveProblemSnapshot.class).restore();
        var config=copy.getStrategy().getConfig();
        config.setSolverEngine(cn.windwheel.aps.entity.enums.SolverEngineEnum.TIMEFOLD);
        for(var resource:copy.getMainResources())resource.getTaskList().removeIf(t->!Boolean.TRUE.equals(t.getPinned()));
        for(var task:copy.getTasks())if(!Boolean.TRUE.equals(task.getPinned()))task.setResource(null).setStartTime(null);
        long remaining=TimeUnit.NANOSECONDS.toMillis(deadline-System.nanoTime());if(remaining<1)return null;
        var solverConfig=new TimefoldManageConfigurator().buildConfig(config);
        solverConfig.setTerminationConfig(new ai.timefold.solver.core.config.solver.termination.TerminationConfig().withSpentLimit(Duration.ofMillis(remaining)));
        var solver=ai.timefold.solver.core.api.solver.SolverFactory.<SchedulingSolution>create(solverConfig).buildSolver();
        var executor=Executors.newSingleThreadScheduledExecutor(r->{Thread t=new Thread(r,"cpsat-timefold-deadline");t.setDaemon(true);return t;});
        var failure=new java.util.concurrent.atomic.AtomicReference<RuntimeException>();
        try {
            executor.scheduleWithFixedDelay(()->{
                try {if(stop.getAsBoolean() || System.nanoTime()>=deadline)solver.terminateEarly();}
                catch(RuntimeException e){failure.set(e);solver.terminateEarly();}
            },0,25,TimeUnit.MILLISECONDS);
            var result=solver.solve(copy);
            if(failure.get()!=null)throw failure.get();
            if(stop.getAsBoolean())throw new CancellationException();
            var score=result.getTimefoldScore();
            return score!=null && score.initScore()==0 && score.isFeasible()?result:null;
        } finally {executor.shutdownNow();}
    }
}
