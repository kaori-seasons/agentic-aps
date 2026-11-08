package cn.windwheel.aps.solve.engine;

import cn.windwheel.aps.solve.model.SchedulingSolution;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.function.BiFunction;
import java.util.function.Consumer;

/** One tenant's one-shot solve. Submission and cancellation are ordered under the same lock. */
public final class ManagedSolverSession implements AutoCloseable {
    public interface Job {
        SchedulingSolution getFinalBestSolution() throws InterruptedException, ExecutionException;
        boolean isTerminatedEarly();
    }

    private final BiFunction<UUID, SchedulingSolution, Job> submit;
    private final Consumer<UUID> terminate;
    private final Runnable close;
    private boolean cancelled;
    private boolean closed;

    public ManagedSolverSession(BiFunction<UUID, SchedulingSolution, Job> submit,
                                Consumer<UUID> terminate, Runnable close) {
        this.submit = submit;
        this.terminate = terminate;
        this.close = close;
    }

    public SchedulingSolution solve(UUID id, SchedulingSolution problem) throws InterruptedException, ExecutionException {
        Job job;
        synchronized (this) {
            if (cancelled || closed) {
                return null;
            }
            job = submit.apply(id, problem);
        }
        SchedulingSolution result = job.getFinalBestSolution();
        synchronized (this) {
            return cancelled || job.isTerminatedEarly() ? null : result;
        }
    }

    public synchronized void terminateEarly(UUID id) {
        cancelled = true;
        if (!closed) {
            terminate.accept(id);
        }
    }

    @Override
    public synchronized void close() {
        if (!closed) {
            closed = true;
            close.run();
        }
    }
}
