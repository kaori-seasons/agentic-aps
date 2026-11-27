package cn.windwheel.aps.shortage;

import cn.windwheel.aps.shortage.ShortageModel.AlertState;
import cn.windwheel.aps.shortage.ShortageModel.Channel;
import cn.windwheel.aps.shortage.ShortageModel.Dimension;
import cn.windwheel.aps.shortage.ShortageModel.ItemState;
import cn.windwheel.aps.shortage.ShortageModel.NotifyState;
import cn.windwheel.aps.shortage.ShortageModel.Receipt;
import cn.windwheel.aps.shortage.ShortageModel.Severity;
import cn.windwheel.aps.shortage.ShortageModel.SupplyMode;
import cn.windwheel.aps.shortage.ShortageRule.Effective;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static cn.windwheel.aps.shortage.ShortageModel.SYSTEM_ACTOR;

/**
 * 预警台账。
 * <p>
 * 欠料条目是事实，预警是从事实推导出来的通知记录：条目 × 规则 × 当前时刻。
 * 去重靠 alertKey（规则、维度、实体、时间桶）加等级的唯一索引；节流靠冷却期；等级升高除外，立即重发并生成新的 alertId。
 * 所有推导都是幂等的，所以可以由任何节点在任何时刻重放：求解线程在 MRP 结束后立刻评一次，
 * 后台任务再周期性复评，既补上落库后崩溃的那一次，也让缺口随时间推移跨过冻结、三日边界时自然升级。
 */
@Slf4j
@Service
public class ShortageAlertService {
    private final ShortageAlertStore alerts;
    private final ShortageNotifyStore notifies;
    private final ShortageItemStore items;
    private final ShortageRuleStore ruleStore;
    private final ShortageRuleEngine engine;
    private final ShortageProperties props;
    private final AlertEnvelope envelope;
    private final ObjectMapper json;
    private final TransactionTemplate tx;

    public ShortageAlertService(ShortageAlertStore alerts, ShortageNotifyStore notifies, ShortageItemStore items,
                                ShortageRuleStore ruleStore, ShortageRuleEngine engine, ShortageProperties props,
                                AlertEnvelope envelope, ObjectMapper json, PlatformTransactionManager transactions) {
        this.alerts = alerts;
        this.notifies = notifies;
        this.items = items;
        this.ruleStore = ruleStore;
        this.engine = engine;
        this.props = props;
        this.envelope = envelope;
        this.json = json;
        this.tx = new TransactionTemplate(transactions);
    }

    /** 一次复评或一轮扫描的计数，手工触发扫描时原样返回给调用方核对。 */
    @Getter
    public static class Outcome {
        private int evaluated;
        private int created;
        private int repeated;
        private int superseded;
        private int resolved;
        private int failed;

        void created() {
            created++;
        }

        void repeated() {
            repeated++;
        }

        void superseded(int count) {
            superseded += count;
        }

        void resolved(int count) {
            resolved += count;
        }

        void failed() {
            failed++;
        }

        public Outcome merge(Outcome other) {
            evaluated += other.evaluated;
            created += other.created;
            repeated += other.repeated;
            superseded += other.superseded;
            resolved += other.resolved;
            failed += other.failed;
            return this;
        }
    }

    public Effective effective(String tenant, ShortageRule rule) {
        return rules(tenant).get(rule);
    }

    public Map<ShortageRule, Effective> rules(String tenant) {
        Map<ShortageRule, Effective> result = new EnumMap<>(ShortageRule.class);
        ruleStore.effective(tenant).forEach(rule -> result.put(rule.rule(), rule));
        return result;
    }

    /** 对给定欠料条目复评。单条失败不影响其它条目，失败的条目留在原状态等下一轮扫描。 */
    public Outcome evaluate(String tenant, Collection<String> keys, LocalDateTime now) {
        Outcome outcome = new Outcome();
        List<String> distinct = keys.stream().filter(Objects::nonNull).distinct().toList();
        if (distinct.isEmpty()) {
            return outcome;
        }
        Map<ShortageRule, Effective> rules = rules(tenant);
        for (ShortageItem item : items.byKeys(tenant, distinct)) {
            try {
                tx.executeWithoutResult(status -> evaluateItem(tenant, item, rules, now, outcome));
                outcome.evaluated++;
            } catch (Exception e) {
                outcome.failed();
                log.error("欠料复评失败，留待下一轮扫描 shortageKey={}", item.getShortageKey(), e);
            }
        }
        return outcome;
    }

    private void evaluateItem(String tenant, ShortageItem item, Map<ShortageRule, Effective> rules,
                              LocalDateTime now, Outcome outcome) {
        String key = item.getShortageKey();
        JsonNode detail = tree(item.getDetail());
        ShortageRuleEngine.Evaluation evaluation = engine.evaluate(item,
                ShortageText.duration(text(detail, "leadTime")), ShortageText.duration(text(detail, "bufferTime")),
                rules::get, now);
        Severity top = evaluation.severity();
        boolean muted = item.getState() == ItemState.MUTED;
        if (muted && top != Severity.P1) {
            // 静默期只更新等级，不新建预警。P1 不允许静默，升到 P1 会落到下面自动解除。
            items.updateEvaluation(tenant, key, top, evaluation.reasonCode(), ItemState.MUTED, now);
            return;
        }
        List<ShortageAlert> open = alerts.openForItems(tenant, List.of(key));
        Set<ShortageRule> matched = evaluation.matches().stream()
                .map(ShortageRuleEngine.Match::rule).collect(Collectors.toSet());
        // 条件已消失的预警关闭，例如在途改期后不再有被跳过的行。
        List<String> cleared = open.stream().filter(a -> !matched.contains(a.getRule()))
                .map(ShortageAlert::getAlertId).toList();
        if (!cleared.isEmpty()) {
            transition(tenant, cleared, AlertState.RESOLVED, SYSTEM_ACTOR, now, "本轮未再命中该规则");
            open.removeIf(a -> cleared.contains(a.getAlertId()));
            outcome.resolved(cleared.size());
        }
        // 等级高的先处理，保证首条预警就是最高等级的那条。
        List<String> superseded = new ArrayList<>();
        String successor = null;
        for (ShortageRuleEngine.Match match : evaluation.matches().stream()
                .sorted(Comparator.comparingInt(m -> m.severity().ordinal())).toList()) {
            Effective rule = rules.get(match.rule());
            ShortageAlert current = open.stream().filter(a -> a.getRule() == match.rule()).findFirst().orElse(null);
            if (current != null && current.getSeverity() == match.severity()) {
                // 已确认的不再按同等级重发：有人在处理，按小时重复推同一条只会训练人忽略它。升级除外。
                boolean acknowledged = current.getState() == AlertState.ACKNOWLEDGED;
                if (acknowledged || !rule.allows(current.getCreatedAt(), now)) {
                    alerts.touch(tenant, current.getAlertId(), now);
                    outcome.repeated();
                    continue;
                }
            } else if (current != null && current.getSeverity().higherThan(match.severity())) {
                // 降级：旧的高等级预警留给人工处理，不为更低的等级另起一条。
                alerts.touch(tenant, current.getAlertId(), now);
                outcome.repeated();
                continue;
            }
            ShortageAlert alert = newAlert(tenant, item, match, rule, now);
            boolean created = alerts.insertOrTouch(alert);
            if (!created) {
                // 同一桶同等级已有一条终态预警，例如刚被解决又在桶内复现：本桶内不再重发。
                alerts.findByKey(tenant, alert.getAlertKey(), match.severity())
                        .ifPresent(existing -> alerts.touch(tenant, existing.getAlertId(), now));
                outcome.repeated();
                continue;
            }
            if (current != null) {
                superseded.add(current.getAlertId());
                successor = alert.getAlertId();
            }
            enqueue(alert, rule, now);
            outcome.created();
        }
        if (!superseded.isEmpty()) {
            transition(tenant, superseded, AlertState.SUPERSEDED, SYSTEM_ACTOR, now, "由预警 " + successor + " 接续");
            outcome.superseded(superseded.size());
        }
        // 有未确认的预警就回到未确认；都确认过就停在已确认；其余情况不改写人工设过的状态。
        List<ShortageAlert> live = alerts.nonTerminalForItems(tenant, List.of(key));
        ItemState next = item.getState();
        if (live.stream().anyMatch(a -> a.getState() == AlertState.OPEN)) {
            next = ItemState.OPEN;
        } else if (live.stream().anyMatch(a -> a.getState() == AlertState.ACKNOWLEDGED)) {
            next = ItemState.ACKNOWLEDGED;
        } else if (muted) {
            // 只有升到 P1 的静默条目才会带着静默状态走到这里。
            next = ItemState.OPEN;
        }
        items.updateEvaluation(tenant, key, top, evaluation.reasonCode(), next, now);
    }

    private ShortageAlert newAlert(String tenant, ShortageItem item, ShortageRuleEngine.Match match, Effective rule,
                                   LocalDateTime now) {
        String entityKey = item.getShortageKey().substring(item.getDimension().name().length() + 1);
        return baseAlert(tenant, item.getShortageKey(), item.getDimension(), match.rule(), entityKey, match.severity(),
                match.title(), write(itemPayload(item, match.rule(), match.severity(), match.title(), rule)), now);
    }

    private ShortageAlert baseAlert(String tenant, String shortageKey, Dimension dimension, ShortageRule rule,
                                    String entityKey, Severity severity, String title, String payload,
                                    LocalDateTime now) {
        return new ShortageAlert()
                .setTenantId(tenant)
                .setAlertId(UUID.randomUUID().toString())
                .setAlertKey(rule.name() + ":" + dimension.name() + ":" + entityKey + ":" + severity.bucket(now))
                .setShortageKey(shortageKey)
                .setRule(rule)
                .setDimension(dimension)
                .setEntityKey(entityKey)
                .setBucket(severity.bucket(now))
                .setSeverity(severity)
                .setTitle(ShortageText.trim(title, 255))
                .setPayload(payload)
                .setState(AlertState.OPEN)
                .setFirstObservedAt(now)
                .setLastObservedAt(now)
                .setCreatedAt(now)
                .setUpdatedAt(now);
    }

    private Map<String, Object> itemPayload(ShortageItem i, ShortageRule rule, Severity severity, String title,
                                            Effective effective) {
        ZoneId zone = props.zone();
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("ruleCode", rule.name());
        p.put("severity", severity.name());
        p.put("dimension", i.getDimension().name());
        p.put("title", title);
        p.put("actionHint", effective.actionHint());
        p.put("responseMinutes", severity.getResponseMinutes());
        p.put("receivers", effective.receivers());
        p.put("source", i.getSource().name());
        p.put("runId", i.getRunId());
        p.put("materialCode", i.getMaterialCode());
        p.put("materialName", i.getMaterialName());
        p.put("unit", i.getUnit());
        p.put("gapQuantity", ShortageText.decimal(i.getGapQuantity()));
        p.put("requiredQuantity", ShortageText.decimal(i.getRequiredQuantity()));
        p.put("requiredAt", ShortageText.iso(i.getRequiredAt(), zone));
        p.put("weekAt", ShortageText.iso(i.getWeekStart()));
        p.put("orderCode", i.getOrderCode());
        p.put("locationCode", i.getLocationCode());
        p.put("reference", i.getReference());
        p.put("sourceReason", i.getSourceReason());
        p.put("onHandConsumed", ShortageText.decimal(i.getOnHandConsumed()));
        p.put("onHandAvailable", ShortageText.decimal(i.getOnHandAvailable()));
        p.put("inTransitConsumed", ShortageText.decimal(i.getInTransitConsumed()));
        p.put("inTransitSkipped", ShortageText.decimal(i.getInTransitSkipped()));
        p.put("skippedLines", array(i.getSkippedLines()));
        p.put("availableInTransit", array(i.getAvailableInTransit()));
        // 空值必须显式呈现为“无到货计划”，不能省略：省略会让计划员以为到货时间待定。
        p.put("supplyMode", i.getSupplyMode() == null ? SupplyMode.NONE.name() : i.getSupplyMode().name());
        p.put("supplyPlan", i.getSupplyMode() == null || i.getSupplyMode() == SupplyMode.NONE ? "无到货计划" : "有到货计划");
        p.put("expectedEta", ShortageText.iso(i.getExpectedEta(), zone));
        p.put("supplierCode", i.getSupplierCode());
        p.put("earliestStart", ShortageText.iso(i.getEarliestStart(), zone));
        p.put("delayDuration", i.getDelayDuration());
        p.put("downstreamTasks", array(i.getDownstreamTasks()));
        p.put("safetyStock", ShortageText.decimal(i.getSafetyStock()));
        p.put("scenarioBalance", ShortageText.decimal(i.getScenarioBalance()));
        p.put("dataCutoff", ShortageText.iso(i.getDataCutoff(), zone));
        p.put("firstDetectedAt", ShortageText.iso(i.getFirstDetectedAt(), zone));
        p.put("observedCount", i.getObservedCount());
        return p;
    }

    /**
     * 新建预警后登记推送。台账在创建时即已送达，是闭环的落点；其余渠道进队列，由后台任务投递。
     */
    private void enqueue(ShortageAlert alert, Effective rule, LocalDateTime now) {
        for (String name : rule.channels()) {
            Channel channel = Channel.valueOf(name);
            switch (channel) {
                case LEDGER -> notifies.insert(push(alert, Channel.LEDGER, "ledger", null, NotifyState.SENT, now));
                case WEBHOOK -> props.getWebhooks().stream()
                        .filter(ShortageProperties.Endpoint::isEnabled)
                        .filter(e -> e.getTenantId() == null || e.getTenantId().isBlank()
                                || e.getTenantId().equals(alert.getTenantId()))
                        .forEach(endpoint -> notifies.insert(push(alert, Channel.WEBHOOK, endpoint.getUrl(),
                                envelope.build(alert, AlertEnvelope.newMessageId(), Channel.WEBHOOK,
                                        envelope.ackUrl(alert.getAlertId())), NotifyState.PENDING, now)));
                case OUTBOX -> {
                    if (props.isOutboxEnabled()) {
                        notifies.insert(push(alert, Channel.OUTBOX, "outbox",
                                envelope.build(alert, AlertEnvelope.newMessageId(), Channel.OUTBOX, null),
                                NotifyState.PENDING, now));
                    }
                }
                case EMAIL -> enqueueMail(alert, rule, now);
                default -> {
                }
            }
        }
    }

    private void enqueueMail(ShortageAlert alert, Effective rule, LocalDateTime now) {
        // 仅 P1、P2 走邮件。
        if (!props.getMail().isEnabled() || !AlertEnvelope.mailEligible(Channel.EMAIL, alert.getSeverity())) {
            return;
        }
        List<String> recipients = new ArrayList<>(new LinkedHashSet<>(rule.receivers().stream()
                .flatMap(role -> props.getRoleRecipients().getOrDefault(role, List.of()).stream())
                .filter(r -> r != null && r.contains("@")).toList()));
        if (recipients.isEmpty()) {
            notifies.insert(push(alert, Channel.EMAIL, "", null, NotifyState.SKIPPED, now)
                    .setLastError("接收角色未配置邮箱"));
            return;
        }
        notifies.insert(push(alert, Channel.EMAIL, String.join(",", recipients), mailText(alert, rule),
                NotifyState.PENDING, now));
    }

    private String mailText(ShortageAlert alert, Effective rule) {
        return "等级：" + alert.getSeverity() + "\n标题：" + alert.getTitle() + "\n建议动作：" + rule.actionHint()
                + "\n预警编号：" + alert.getAlertId() + "\n响应时限：" + alert.getSeverity().getResponseMinutes()
                + " 分钟内\n请登录 APS 预警台账确认与处理。";
    }

    private ShortageNotify push(ShortageAlert alert, Channel channel, String target, String payload,
                                  NotifyState state, LocalDateTime now) {
        return new ShortageNotify()
                .setTenantId(alert.getTenantId())
                .setId(UUID.randomUUID().toString())
                .setAlertId(alert.getAlertId())
                .setChannel(channel)
                .setTarget(target)
                .setIdempotencyKey(ShortageNotifyStore.idempotencyKey(alert.getAlertId(), channel, target))
                .setState(state)
                .setSentAt(state == NotifyState.SENT ? now : null)
                .setAvailableAt(now)
                .setPayload(payload)
                .setCreatedAt(now);
    }

    /**
     * 系统级预警：数据质量、发布阻断、推送失败。没有欠料条目可挂，按规则加实体去重，
     * 去重、冷却、已确认不重发、升级立即重发都与条目级预警同一套规则。
     *
     * @return true 表示新建了预警
     */
    @Transactional(rollbackFor = Exception.class)
    public boolean raiseSystem(String tenant, ShortageRule rule, String entityKey, Severity severity, String title,
                               Map<String, Object> facts, LocalDateTime now) {
        Effective effective = effective(tenant, rule);
        if (!effective.enabled()) {
            return false;
        }
        if (severity == null) {
            severity = effective.severity();
        }
        List<ShortageAlert> open = alerts.openForEntity(tenant, rule, entityKey);
        ShortageAlert current = open.isEmpty() ? null : open.get(0);
        if (current != null && current.getSeverity() == severity
                && (current.getState() == AlertState.ACKNOWLEDGED || !effective.allows(current.getCreatedAt(), now))) {
            alerts.touch(tenant, current.getAlertId(), now);
            return false;
        }
        if (current != null && current.getSeverity().higherThan(severity)) {
            alerts.touch(tenant, current.getAlertId(), now);
            return false;
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("ruleCode", rule.name());
        payload.put("severity", severity.name());
        payload.put("dimension", Dimension.SYSTEM.name());
        payload.put("title", ShortageText.trim(title, 255));
        payload.put("actionHint", effective.actionHint());
        payload.put("responseMinutes", severity.getResponseMinutes());
        payload.put("receivers", effective.receivers());
        payload.putAll(facts == null ? Map.of() : facts);
        ShortageAlert alert = baseAlert(tenant, null, Dimension.SYSTEM, rule, entityKey, severity, title,
                write(payload), now);
        if (!alerts.insertOrTouch(alert)) {
            alerts.findByKey(tenant, alert.getAlertKey(), severity)
                    .ifPresent(existing -> alerts.touch(tenant, existing.getAlertId(), now));
            return false;
        }
        if (current != null) {
            transition(tenant, List.of(current.getAlertId()), AlertState.SUPERSEDED, SYSTEM_ACTOR, now,
                    "由预警 " + alert.getAlertId() + " 接续");
        }
        enqueue(alert, effective, now);
        return true;
    }

    /**
     * 计划发布被阻断：现有代码里唯一的阻断点是确认时基础数据签名已变化。
     * 必须走独立事务：调用方的确认事务会随阻断异常回滚，跟着它一起写就留不下痕迹。
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void raiseReleaseBlocked(String tenant, String reason, LocalDateTime now) {
        raiseSystem(tenant, ShortageRule.MRP_RELEASE_BLOCKED, "RELEASE", null, "计划发布被阻断：" + reason,
                Map.of("reason", reason), now);
    }

    /** 系统级条件消失时关闭对应预警。 */
    @Transactional(rollbackFor = Exception.class)
    public int clearSystem(String tenant, ShortageRule rule, String entityKey, LocalDateTime now, String note) {
        List<String> ids = ids(alerts.openForEntity(tenant, rule, entityKey));
        transition(tenant, ids, AlertState.RESOLVED, SYSTEM_ACTOR, now, note);
        return ids.size();
    }

    /**
     * 条目级回执：确认、解决、静默。P1 不允许静默，静默会把“已经上线受阻”压成噪声。
     */
    @Transactional(rollbackFor = Exception.class)
    public void handleItem(String tenant, String shortageKey, Receipt action, String by, String note,
                           LocalDateTime now) {
        ShortageItem item = items.find(tenant, shortageKey).orElseThrow(() -> ShortageException.missing("欠料条目"));
        List<ShortageAlert> live = alerts.nonTerminalForItems(tenant, List.of(shortageKey));
        AlertState target;
        switch (action) {
            case MUTED -> {
                requireMuteAllowed(item, live, note);
                target = AlertState.MUTED;
            }
            case RESOLVED -> target = AlertState.RESOLVED;
            case ACK -> target = AlertState.ACKNOWLEDGED;
            default -> throw new ShortageException(422, "条目级回执只支持 ACK、RESOLVED、MUTED");
        }
        items.transition(tenant, shortageKey, ItemState.valueOf(target.name()), by, now, note);
        transition(tenant, ids(live), target, by, now, note);
        notifies.receipt(tenant, ids(live), action, note, by, now);
    }

    private void requireMuteAllowed(ShortageItem item, List<ShortageAlert> live, String note) {
        boolean p1 = item.getSeverity() == Severity.P1 || live.stream().anyMatch(a -> a.getSeverity() == Severity.P1);
        if (p1) {
            throw new ShortageException(409, "P1 预警不允许静默：已影响上线的缺口不能被压成噪声");
        }
        if (note == null || note.isBlank()) {
            throw new ShortageException(422, "静默必须填写理由");
        }
    }

    /**
     * 预警级回执，供渠道回调与外部系统使用。alertId 是系统签发的，调用前必须已通过会话或签名认证。
     */
    @Transactional(rollbackFor = Exception.class)
    public ShortageAlert receipt(String tenant, String alertId, Receipt action, String by, String note,
                                 LocalDateTime now) {
        ShortageAlert alert = alerts.find(tenant, alertId).orElseThrow(() -> ShortageException.missing("预警"));
        AlertState target;
        switch (action) {
            case MUTED -> {
                ShortageItem item = alert.getShortageKey() == null ? null
                        : items.find(tenant, alert.getShortageKey()).orElse(null);
                requireMuteAllowed(item == null ? new ShortageItem().setSeverity(alert.getSeverity()) : item,
                        List.of(alert), note);
                target = AlertState.MUTED;
            }
            case RESOLVED -> target = AlertState.RESOLVED;
            case ACK -> target = AlertState.ACKNOWLEDGED;
            default -> throw new ShortageException(422, "预警级回执只支持 ACK、RESOLVED、MUTED");
        }
        transition(tenant, List.of(alertId), target, by, now, note);
        notifies.receipt(tenant, List.of(alertId), action, note, by, now);
        syncItemState(tenant, alert, action, by, note, now);
        return alerts.find(tenant, alertId).orElse(alert);
    }

    /** 条目状态跟随它的预警：还有未确认的就未确认，都确认过就停在已确认，全部终结则取回执的终态。 */
    private void syncItemState(String tenant, ShortageAlert alert, Receipt action, String by, String note,
                               LocalDateTime now) {
        if (alert.getShortageKey() == null) {
            return;
        }
        String key = alert.getShortageKey();
        if (items.find(tenant, key).isEmpty()) {
            return;
        }
        List<ShortageAlert> live = alerts.nonTerminalForItems(tenant, List.of(key));
        boolean openLeft = live.stream().anyMatch(a -> a.getState() == AlertState.OPEN);
        boolean acknowledgedLeft = live.stream().anyMatch(a -> a.getState() == AlertState.ACKNOWLEDGED);
        ItemState state = openLeft ? ItemState.OPEN
                : acknowledgedLeft ? ItemState.ACKNOWLEDGED
                : action == Receipt.RESOLVED ? ItemState.RESOLVED
                : action == Receipt.MUTED ? ItemState.MUTED
                : null;
        if (state != null) {
            items.transition(tenant, key, state, by, now, note);
        }
    }

    /**
     * 预警进入终态并取消还没投出的推送：已经解决、静默或被接续的预警，不该再被推出去。
     */
    @Transactional(rollbackFor = Exception.class)
    public void retire(String tenant, List<String> alertIds, AlertState state, String by, LocalDateTime now,
                       String note) {
        if (state == AlertState.OPEN || state == AlertState.ACKNOWLEDGED) {
            throw new IllegalArgumentException("retire 只用于终态");
        }
        transition(tenant, alertIds, state, by, now, note);
    }

    private void transition(String tenant, List<String> alertIds, AlertState state, String by, LocalDateTime now,
                            String note) {
        alerts.transition(tenant, alertIds, state, by, now, note);
        if (state != AlertState.ACKNOWLEDGED) {
            notifies.cancelPending(tenant, alertIds, "预警已" + state.name() + "：" + note);
        }
    }

    private static List<String> ids(List<ShortageAlert> list) {
        return list.stream().map(ShortageAlert::getAlertId).toList();
    }

    private JsonNode tree(String text) {
        if (text == null || text.isBlank()) {
            return json.createObjectNode();
        }
        try {
            return json.readTree(text);
        } catch (Exception e) {
            return json.createObjectNode();
        }
    }

    private List<?> array(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        try {
            return json.readValue(text, List.class);
        } catch (Exception e) {
            return List.of();
        }
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }

    private String write(Map<String, Object> payload) {
        try {
            return json.writeValueAsString(payload);
        } catch (Exception e) {
            throw new IllegalStateException("预警载荷无法序列化", e);
        }
    }
}
