package cn.windwheel.aps.config;

import cn.windwheel.aps.demand.PlanningJobs;
import cn.windwheel.aps.demand.PlanningStore;
import cn.windwheel.common.model.AuthContext;
import cn.windwheel.common.utils.AuthContextHolder;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@EnableScheduling
@RequiredArgsConstructor
@ConditionalOnProperty(name = "aps.demand.jobs-enabled", havingValue = "true")
public class DemandPlanningWorker {
    private final PlanningStore plans;
    private final PlanningJobs jobs;

    @Scheduled(fixedDelayString = "${aps.demand.poll-ms:1000}")
    public void poll() {
        jobs.claim()
                .ifPresent(
                        ticket -> {
                            AuthContextHolder.setAuthContext(
                                    new AuthContext()
                                            .setTenantId(ticket.tenant())
                                            .setUserId(ticket.actor()));
                            try {
                                var run = plans.calculate(ticket.request());
                                jobs.finish(ticket, "DONE", run.id(), null);
                            } catch (PlanningStore.Conflict stale) {
                                jobs.finish(ticket, "SUPERSEDED", null, "输入版本或幂等键已变更");
                            } catch (IllegalArgumentException | PlanningStore.Missing invalid) {
                                jobs.finish(ticket, "FAILED", null, "输入或关联版本无效");
                            } catch (RuntimeException failure) {
                                log.warn(
                                        "需求计划作业失败 job={} attempt={} error={}",
                                        ticket.id(),
                                        ticket.attempts(),
                                        failure.getClass().getSimpleName());
                                jobs.finish(
                                        ticket,
                                        ticket.attempts() < 3 ? "QUEUED" : "FAILED",
                                        null,
                                        "执行失败，请核查作业记录");
                            } finally {
                                AuthContextHolder.clear();
                            }
                        });
    }
}
