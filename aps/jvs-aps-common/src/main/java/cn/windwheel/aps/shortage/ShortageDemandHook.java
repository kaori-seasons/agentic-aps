package cn.windwheel.aps.shortage;

import cn.windwheel.aps.demand.DemandPlanner;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 补货运行到欠料清单的挂接点。挂在补货事务提交之后：运行没有提交成功，就不该有对应的欠料条目。
 * 落库走独立事务，任何失败只记日志，不回滚也不拖慢补货。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ShortageDemandHook {
    private final ShortageItemService items;

    public void runCompleted(String tenant, String runId, String location, String material, String unit,
                             DemandPlanner.Result result, Instant asOf, String zone) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            apply(tenant, runId, location, material, unit, result, asOf, zone);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                apply(tenant, runId, location, material, unit, result, asOf, zone);
            }
        });
    }

    private void apply(String tenant, String runId, String location, String material, String unit,
                       DemandPlanner.Result result, Instant asOf, String zone) {
        try {
            List<String> keys = items.recordDemandRun(tenant, runId, location, material, unit, result, asOf, zone);
            items.evaluate(tenant, keys, LocalDateTime.now());
        } catch (Exception e) {
            log.error("需求侧缺料清单落库失败，不影响补货运行 runId={} location={} material={}",
                    runId, location, material, e);
        }
    }
}
