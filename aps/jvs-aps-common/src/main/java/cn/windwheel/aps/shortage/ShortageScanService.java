package cn.windwheel.aps.shortage;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 复评编排。周期任务与手工触发走同一条路径，区别只是手工触发会在最后立刻投递一轮并把计数返回。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ShortageScanService {
    private final ShortageItemStore items;
    private final ShortageAlertService alertService;
    private final ShortageDataQuality dataQuality;
    private final ShortageNotifyDispatcher dispatcher;
    private final ShortageProperties props;

    public record Report(String tenant, LocalDateTime at, int itemsSeen, ShortageAlertService.Outcome alerts,
                         ShortageDataQuality.Report dataQuality, ShortageNotifyDispatcher.Outcome dispatch) {}

    /** 单个租户：未关闭的条目全部重新过一遍规则，再评数据质量规则。 */
    public ShortageAlertService.Outcome sweep(String tenant, LocalDateTime now) {
        List<String> keys = items.forSweep(tenant, now, props.getSweepBatch()).stream()
                .map(ShortageItem::getShortageKey).toList();
        ShortageAlertService.Outcome outcome = alertService.evaluate(tenant, keys, now);
        dataQuality.evaluate(tenant, now, outcome);
        return outcome;
    }

    /** 所有有待复评条目的租户各跑一轮。单个租户失败不影响其它租户。 */
    public ShortageAlertService.Outcome sweepAll(LocalDateTime now) {
        ShortageAlertService.Outcome total = new ShortageAlertService.Outcome();
        for (String tenant : items.activeTenants()) {
            try {
                total.merge(sweep(tenant, now));
            } catch (Exception e) {
                log.error("缺料复评失败 tenant={}", tenant, e);
            }
        }
        return total;
    }

    public Report scan(String tenant, LocalDateTime now) {
        ShortageAlertService.Outcome alerts;
        ShortageDataQuality.Report quality;
        try {
            List<String> keys = items.forSweep(tenant, now, props.getSweepBatch()).stream()
                    .map(ShortageItem::getShortageKey).toList();
            alerts = alertService.evaluate(tenant, keys, now);
            quality = dataQuality.evaluate(tenant, now, alerts);
        } catch (Exception e) {
            throw new ShortageException(500, "复评失败：" + e.getMessage());
        }
        return new Report(tenant, now, alerts.getEvaluated(), alerts, quality, dispatcher.dispatch(tenant, now));
    }
}
