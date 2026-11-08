package cn.windwheel.aps.solve.engine;

import cn.windwheel.aps.solve.model.SchedulingSolution;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class ManagedSolverSessionTest {
    @Test
    void cancellationBeforeSubmissionNeverStartsAJobAndCloseIsIdempotent() throws Exception {
        AtomicInteger submitted = new AtomicInteger();
        AtomicInteger closed = new AtomicInteger();
        ManagedSolverSession session = new ManagedSolverSession((id, problem) -> {
            submitted.incrementAndGet();
            throw new AssertionError("Must not submit");
        }, id -> { }, closed::incrementAndGet);
        UUID id = UUID.randomUUID();
        session.terminateEarly(id);
        assertNull(session.solve(id, new SchedulingSolution()));
        session.close();
        session.close();
        assertEquals(0, submitted.get());
        assertEquals(1, closed.get());
    }

    @Test
    void cancelledResultCannotBeSavedAsSuccessfulPlan() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch finished = new CountDownLatch(1);
        ManagedSolverSession session = new ManagedSolverSession((id, problem) -> new ManagedSolverSession.Job() {
            public SchedulingSolution getFinalBestSolution() throws InterruptedException {
                started.countDown();
                if (!finished.await(5, TimeUnit.SECONDS)) throw new AssertionError("Cancellation timed out");
                return problem;
            }
            public boolean isTerminatedEarly() { return false; }
        }, id -> finished.countDown(), () -> { });
        UUID id = UUID.randomUUID();
        try (session) {
            CompletableFuture<SchedulingSolution> future = CompletableFuture.supplyAsync(() -> {
                try {
                    return session.solve(id, new SchedulingSolution());
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });
            assertTrue(started.await(5, TimeUnit.SECONDS));
            session.terminateEarly(id);
            assertNull(future.get(5, TimeUnit.SECONDS));
        } finally {
            finished.countDown();
        }
    }
}
