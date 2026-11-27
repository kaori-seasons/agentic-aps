package cn.windwheel.aps.shortage;

import cn.windwheel.aps.shortage.ShortageModel.Channel;
import cn.windwheel.aps.shortage.ShortageProperties.Endpoint;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 推送投递。认领在事务里，发送在事务外：带着数据库锁去等一个 10 秒超时的外呼，等于自己制造阻塞。
 * <p>
 * 失败按 1、5、25 分钟退避重试三次，仍失败进 DEAD 并生成元预警。单接收端每分钟限流，超出的排队不丢。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ShortageNotifyDispatcher {
    private final ShortageNotifyStore notifies;
    private final ShortageAlertStore alerts;
    private final ShortageAlertService alertService;
    private final ShortageProperties props;
    private final ShortageWebhookSender webhook;
    private final ObjectProvider<MailGateway> mail;

    @Getter
    public static class Outcome {
        private int claimed;
        private int sent;
        private int failed;
        private int dead;
        private int deferred;

        Outcome merge(Outcome other) {
            claimed += other.claimed;
            sent += other.sent;
            failed += other.failed;
            dead += other.dead;
            deferred += other.deferred;
            return this;
        }
    }

    /** 所有有待投递条目的租户各跑一轮。 */
    public Outcome dispatchDue(LocalDateTime now) {
        Outcome outcome = new Outcome();
        for (String tenant : safeTenants(now)) {
            outcome.merge(dispatch(tenant, now));
        }
        return outcome;
    }

    private List<String> safeTenants(LocalDateTime now) {
        try {
            return notifies.tenantsWithDue(now);
        } catch (Exception e) {
            log.error("读取待投递租户失败", e);
            return List.of();
        }
    }

    public Outcome dispatch(String tenant, LocalDateTime now) {
        Outcome outcome = new Outcome();
        // 先数再认领：认领会把行置为发送中，之后再数就把这一批自己也算进限流窗口了。
        Map<String, Integer> used = notifies.recentPerTarget(tenant, Channel.WEBHOOK, now.minusMinutes(1));
        List<ShortageNotify> claimed = notifies.claim(tenant, now, props.getWebhook().getLeaseSeconds(),
                props.getDispatchBatch());
        outcome.claimed += claimed.size();
        List<ShortageNotify> deferred = new ArrayList<>();
        for (ShortageNotify n : claimed) {
            if (n.getChannel() == Channel.WEBHOOK) {
                int count = used.getOrDefault(n.getTarget(), 0);
                if (count >= props.getWebhook().getPerMinute()) {
                    deferred.add(n);
                    continue;
                }
                used.put(n.getTarget(), count + 1);
            }
            deliver(tenant, n, now, outcome);
        }
        // 每行的租约令牌各不相同，必须逐行放回队列。
        deferred.forEach(n -> notifies.defer(tenant, List.of(n.getId()), n.getLeaseToken(), now.plusSeconds(30)));
        outcome.deferred += deferred.size();
        return outcome;
    }

    private void deliver(String tenant, ShortageNotify n, LocalDateTime now, Outcome outcome) {
        ShortageWebhookSender.Delivery delivery;
        try {
            delivery = switch (n.getChannel()) {
                case WEBHOOK -> send(endpoint(tenant, n.getTarget()), n.getPayload());
                case EMAIL -> sendMail(tenant, n);
                default -> ShortageWebhookSender.Delivery.failed(null, "不支持的推送渠道：" + n.getChannel());
            };
        } catch (Exception e) {
            log.error("推送投递异常 notifyId={}", n.getId(), e);
            delivery = ShortageWebhookSender.Delivery.failed(null, "投递异常：" + e.getClass().getSimpleName());
        }
        if (delivery.accepted()) {
            notifies.sent(tenant, n.getId(), n.getLeaseToken(), now, delivery.status());
            alerts.markPushed(tenant, n.getAlertId(), now);
            outcome.sent++;
            return;
        }
        Duration backoff = props.backoff(n.getAttempts());
        String detail = ShortageText.trim(delivery.detail(), 500);
        notifies.failed(tenant, n.getId(), n.getLeaseToken(), now, delivery.status(), detail,
                backoff == null ? null : now.plus(backoff));
        if (backoff == null) {
            outcome.dead++;
            deliveryFailed(tenant, n, detail, now);
        } else {
            outcome.failed++;
            log.warn("推送失败，{} 后第 {} 次重试 notifyId={} channel={} reason={}", backoff, n.getAttempts() + 1,
                    n.getId(), n.getChannel(), detail);
        }
    }

    /** 接收端可能已被停用或改了地址：找不到配置就按失败处理，不往未知地址发。 */
    private Endpoint endpoint(String tenant, String url) {
        return props.getWebhooks().stream()
                .filter(Endpoint::isEnabled)
                .filter(e -> e.getTenantId() == null || e.getTenantId().isBlank() || e.getTenantId().equals(tenant))
                .filter(e -> url.equals(e.getUrl()))
                .findFirst()
                .orElse(null);
    }

    private ShortageWebhookSender.Delivery send(Endpoint endpoint, String body) {
        return endpoint == null ? ShortageWebhookSender.Delivery.failed(null, "接收端已停用或地址已变更")
                : webhook.send(endpoint, body);
    }

    private ShortageWebhookSender.Delivery sendMail(String tenant, ShortageNotify n) {
        MailGateway gateway = mail.getIfAvailable();
        if (gateway == null) {
            return ShortageWebhookSender.Delivery.failed(null, "邮件通道未启用：缺少邮件实现");
        }
        ShortageAlert alert = alerts.find(tenant, n.getAlertId()).orElse(null);
        List<String> recipients = ShortageRule.split(n.getTarget());
        String error = gateway.send(props.getMail().getFrom(), recipients,
                alert == null ? "APS 缺料预警" : "[APS " + alert.getSeverity() + "] " + alert.getTitle(), n.getPayload());
        return error == null ? new ShortageWebhookSender.Delivery(true, null, null)
                : ShortageWebhookSender.Delivery.failed(null, error);
    }

    /**
     * 重试用尽后的元预警。只进台账，不再往同一个失效渠道外推。
     */
    private void deliveryFailed(String tenant, ShortageNotify n, String detail, LocalDateTime now) {
        log.error("推送重试用尽 notifyId={} alertId={} channel={} reason={}", n.getId(), n.getAlertId(),
                n.getChannel(), detail);
        try {
            Map<String, Object> facts = new LinkedHashMap<>();
            facts.put("notifyId", n.getId());
            facts.put("alertId", n.getAlertId());
            facts.put("channel", n.getChannel().name());
            facts.put("target", ShortageText.trim(n.getTarget(), 500));
            facts.put("attempts", n.getAttempts());
            facts.put("lastError", detail);
            alertService.raiseSystem(tenant, ShortageRule.NOTIFY_DELIVERY_FAILED, "DELIVERY:" + n.getId(), null,
                    "预警推送失败：" + n.getChannel() + " 重试 " + n.getAttempts() + " 次仍未送达，" + detail, facts, now);
        } catch (Exception e) {
            log.error("登记推送失败元预警失败 notifyId={}", n.getId(), e);
        }
    }

}
