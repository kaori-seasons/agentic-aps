package cn.windwheel.aps.solve.config;

import cn.windwheel.aps.entity.enums.SolverEngineEnum;
import cn.windwheel.aps.solve.model.SchedulingSolution;
import cn.windwheel.aps.solve.timefold.TimefoldManageConfigurator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import java.util.UUID;
import java.util.concurrent.ExecutionException;

@Component
@RequiredArgsConstructor
public class SchedulingEngineRouter {
    private final SolveManageConfigurator legacy;
    private final TimefoldManageConfigurator timefold;

    public SchedulingSolution solve(UUID planId, SchedulingSolution problem) throws InterruptedException, ExecutionException {
        return solve(planId,problem,null);
    }

    public SchedulingSolution solve(UUID planId, SchedulingSolution problem, java.util.function.BooleanSupplier cancelled)
            throws InterruptedException, ExecutionException {
        if (cancelled != null && cancelled.getAsBoolean()) { return null; }
        if (problem.getStrategy().getConfig().getSolverEngine() == SolverEngineEnum.TIMEFOLD) {
            var manager = timefold.initSolverManager(problem.getStrategy());
            try {
                var job = manager.solve(planId, problem);
                try (CancellationMonitor monitor = new CancellationMonitor(cancelled, () -> manager.terminateEarly(planId))) {
                    SchedulingSolution result = job.getFinalBestSolution();
                    monitor.checkFailure();
                    if (job.isTerminatedEarly()) { return null; }
                    var score = result.getTimefoldScore();
                    if (score == null || score.initScore() != 0 || !score.isFeasible()) {
                        throw new IllegalStateException("Timefold未找到硬约束可行解");
                    }
                    result.setScore(org.optaplanner.core.api.score.buildin.hardsoftlong.HardSoftLongScore
                            .ofUninitialized(score.initScore(), score.hardScore(), score.softScore()));
                    return result;
                }
            } finally {
                timefold.release();
            }
        }
        var manager = legacy.initSolverManager(problem.getStrategy());
        try {
            var job = manager.solve(planId, problem);
            try (CancellationMonitor monitor = new CancellationMonitor(cancelled, () -> manager.terminateEarly(planId))) {
                SchedulingSolution result = job.getFinalBestSolution();
                monitor.checkFailure();
                if (job.isTerminatedEarly()) { return null; }
                if (result.getScore() == null || result.getScore().initScore() != 0 || !result.getScore().isFeasible()) {
                    throw new IllegalStateException("OptaPlanner未找到硬约束可行解");
                }
                return result;
            }
        } finally {
            legacy.release();
        }
    }

    private static final class CancellationMonitor implements AutoCloseable {
        private final java.util.concurrent.ScheduledExecutorService executor;
        private final java.util.concurrent.atomic.AtomicReference<RuntimeException> failure = new java.util.concurrent.atomic.AtomicReference<>();

        private CancellationMonitor(java.util.function.BooleanSupplier cancelled, Runnable terminate) {
            if (cancelled == null) { executor = null; return; }
            executor = java.util.concurrent.Executors.newSingleThreadScheduledExecutor(runnable -> {
                Thread thread = new Thread(runnable,"aps-cancel-monitor");
                thread.setDaemon(true);
                return thread;
            });
            executor.scheduleWithFixedDelay(() -> {
                try {
                    if (cancelled.getAsBoolean()) { terminate.run(); }
                } catch (RuntimeException exception) {
                    failure.compareAndSet(null,exception);
                    terminate.run();
                }
            },0,250,java.util.concurrent.TimeUnit.MILLISECONDS);
        }

        private void checkFailure() {
            if (failure.get() != null) { throw new IllegalStateException("取消状态不可用，已停止求解",failure.get()); }
        }

        public void close() {
            if (executor != null) { executor.shutdownNow(); }
        }
    }

    public void cancel(UUID planId) {
        var legacyManager = legacy.getSolverManager();
        if (legacyManager != null) { legacyManager.terminateEarly(planId); }
        var timefoldManager = timefold.getSolverManager();
        if (timefoldManager != null) { timefoldManager.terminateEarly(planId); }
    }
}
