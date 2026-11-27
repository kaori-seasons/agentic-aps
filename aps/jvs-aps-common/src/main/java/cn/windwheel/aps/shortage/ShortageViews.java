package cn.windwheel.aps.shortage;

import cn.windwheel.aps.shortage.ShortageModel.Severity;
import cn.windwheel.aps.shortage.ShortageRule.Effective;
import cn.windwheel.aps.shortage.ShortageRuleStore.Change;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 对外视图。线上口径：数量一律十进制字符串，时间一律带时区偏移的 ISO-8601，JSON 列解析后再输出。
 * 库里的列名是 state，对统一律叫 status，与方案的 SHORTAGE_ITEM 标准表一致。
 */
@Component
public class ShortageViews {
    private final ObjectMapper json;
    private final ShortageProperties props;

    public ShortageViews(ObjectMapper json, ShortageProperties props) {
        this.json = json;
        this.props = props;
    }

    private ZoneId zone() {
        return props.zone();
    }

    public Map<String, Object> item(ShortageItem i) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("shortageKey", i.getShortageKey());
        m.put("dimension", i.getDimension().name());
        m.put("source", i.getSource().name());
        m.put("runId", i.getRunId());
        m.put("detectedAt", ShortageText.iso(i.getDetectedAt(), zone()));
        m.put("firstDetectedAt", ShortageText.iso(i.getFirstDetectedAt(), zone()));
        m.put("dataCutoff", ShortageText.iso(i.getDataCutoff(), zone()));
        m.put("severity", i.getSeverity() == null ? null : i.getSeverity().name());
        m.put("reasonCode", i.getReasonCode());
        m.put("materialCode", i.getMaterialCode());
        m.put("materialName", i.getMaterialName());
        m.put("unit", i.getUnit());
        m.put("gapQuantity", ShortageText.decimal(i.getGapQuantity()));
        m.put("requiredQuantity", ShortageText.decimal(i.getRequiredQuantity()));
        m.put("requiredAt", ShortageText.iso(i.getRequiredAt(), zone()));
        m.put("weekAt", ShortageText.iso(i.getWeekStart()));
        m.put("orderCode", i.getOrderCode());
        m.put("locationCode", i.getLocationCode());
        m.put("reference", i.getReference());
        m.put("sourceReason", i.getSourceReason());
        m.put("onHandConsumed", ShortageText.decimal(i.getOnHandConsumed()));
        m.put("onHandAvailable", ShortageText.decimal(i.getOnHandAvailable()));
        m.put("inTransitConsumed", ShortageText.decimal(i.getInTransitConsumed()));
        m.put("inTransitSkipped", ShortageText.decimal(i.getInTransitSkipped()));
        m.put("skippedLines", parse(i.getSkippedLines()));
        m.put("availableInTransit", parse(i.getAvailableInTransit()));
        m.put("supplyMode", i.getSupplyMode() == null ? null : i.getSupplyMode().name());
        // 空值显式呈现为“无到货计划”，不能省略。
        m.put("supplyPlan", i.getSupplyMode() == cn.windwheel.aps.shortage.ShortageModel.SupplyMode.NONE
                || i.getSupplyMode() == null ? "无到货计划" : "有到货计划");
        m.put("expectedEta", ShortageText.iso(i.getExpectedEta(), zone()));
        m.put("supplierCode", i.getSupplierCode());
        m.put("earliestStart", ShortageText.iso(i.getEarliestStart(), zone()));
        m.put("delayDuration", i.getDelayDuration());
        m.put("downstreamTasks", parse(i.getDownstreamTasks()));
        m.put("safetyStock", ShortageText.decimal(i.getSafetyStock()));
        m.put("scenarioBalance", ShortageText.decimal(i.getScenarioBalance()));
        m.put("detail", parse(i.getDetail()));
        m.put("status", i.getState().name());
        m.put("observedCount", i.getObservedCount());
        m.put("lastEvaluatedAt", ShortageText.iso(i.getLastEvaluatedAt(), zone()));
        m.put("resolvedBy", i.getResolvedBy());
        m.put("resolvedAt", ShortageText.iso(i.getResolvedAt(), zone()));
        m.put("resolutionNote", i.getResolutionNote());
        m.put("createdAt", ShortageText.iso(i.getCreatedAt(), zone()));
        m.put("updatedAt", ShortageText.iso(i.getUpdatedAt(), zone()));
        return m;
    }

    public Map<String, Object> alert(ShortageAlert a) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("alertId", a.getAlertId());
        m.put("alertKey", a.getAlertKey());
        m.put("shortageKey", a.getShortageKey());
        m.put("ruleCode", a.getRule().name());
        m.put("dimension", a.getDimension().name());
        m.put("entityKey", a.getEntityKey());
        m.put("bucket", a.getBucket());
        m.put("severity", a.getSeverity().name());
        m.put("title", a.getTitle());
        m.put("payload", parse(a.getPayload()));
        m.put("status", a.getState().name());
        m.put("observedCount", a.getObservedCount());
        m.put("firstObservedAt", ShortageText.iso(a.getFirstObservedAt(), zone()));
        m.put("lastObservedAt", ShortageText.iso(a.getLastObservedAt(), zone()));
        m.put("lastPushedAt", ShortageText.iso(a.getLastPushedAt(), zone()));
        m.put("pushedCount", a.getPushedCount());
        m.put("acknowledgedBy", a.getAcknowledgedBy());
        m.put("acknowledgedAt", ShortageText.iso(a.getAcknowledgedAt(), zone()));
        m.put("resolutionNote", a.getResolutionNote());
        m.put("createdAt", ShortageText.iso(a.getCreatedAt(), zone()));
        return m;
    }

    /** 列表不带推送原文，原文可能很大；单条查询与出站拉取才带。 */
    public Map<String, Object> notify(ShortageNotify n, boolean withPayload) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", n.getId());
        m.put("alertId", n.getAlertId());
        m.put("channel", n.getChannel().name());
        m.put("target", n.getTarget());
        m.put("idempotencyKey", n.getIdempotencyKey());
        m.put("status", n.getState().name());
        m.put("attempts", n.getAttempts());
        m.put("availableAt", ShortageText.iso(n.getAvailableAt(), zone()));
        m.put("lastAttemptAt", ShortageText.iso(n.getLastAttemptAt(), zone()));
        m.put("sentAt", ShortageText.iso(n.getSentAt(), zone()));
        m.put("httpStatus", n.getHttpStatus());
        m.put("lastError", n.getLastError());
        m.put("receiptState", n.getReceiptState() == null ? null : n.getReceiptState().name());
        m.put("receiptNote", n.getReceiptNote());
        m.put("receiptBy", n.getReceiptBy());
        m.put("receiptAt", ShortageText.iso(n.getReceiptAt(), zone()));
        m.put("createdAt", ShortageText.iso(n.getCreatedAt(), zone()));
        if (withPayload) {
            m.put("payload", parse(n.getPayload()));
        }
        return m;
    }

    /** 生效规则连同覆盖值一起返回，界面才能分清哪些是默认、哪些是租户改过的。 */
    public Map<String, Object> rule(Effective e, Change override) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("ruleCode", e.rule().name());
        m.put("dimension", e.rule().getDimension().name());
        m.put("system", e.rule().isSystem());
        m.put("defaultSeverity", e.rule().getSeverity().name());
        m.put("defaultEnabled", e.rule().isEnabled());
        m.put("defaultThresholdValue", e.rule().getThresholdValue());
        m.put("defaultThresholdUnit", e.rule().getThresholdUnit());
        m.put("defaultCooldownMinutes", ShortageRule.DEFAULT_COOLDOWN_MINUTES);
        m.put("defaultReceivers", e.rule().receiverList());
        m.put("defaultChannels", e.rule().channelList());
        m.put("defaultActionHint", e.rule().getActionHint());
        m.put("severity", e.severity().name());
        m.put("enabled", e.enabled());
        // 生效阈值统一折算成整数与单位。三元表达式里混用 int 与 null 会被自动拆箱，
        // 无阈值的规则（如 MRP_NO_SUPPLY）就 NPE 了，所以用分支语句显式装箱。
        Integer value;
        String unit;
        if (e.thresholdDays() != null) {
            value = Math.toIntExact(e.thresholdDays());
            unit = "DAYS";
        } else if (e.thresholdMinutes() != null) {
            value = Math.toIntExact(e.thresholdMinutes());
            unit = "MINUTES";
        } else {
            value = null;
            unit = null;
        }
        m.put("thresholdValue", value);
        m.put("thresholdUnit", unit);
        m.put("cooldownMinutes", e.cooldownMinutes());
        m.put("responseMinutes", e.severity().getResponseMinutes());
        m.put("receivers", e.receivers());
        m.put("channels", e.channels());
        m.put("actionHint", e.actionHint());
        m.put("overridden", override != null);
        if (override != null) {
            m.put("override", override);
        }
        return m;
    }

    /**
     * 分维度汇总。界面要默认按线分组并折叠水位线，靠的是这份汇总，而不是把两条线混在一张列表里。
     * 等级只统计未关闭的条目：已解决的缺口不该再计入“还有多少 P1”。
     */
    public Map<String, Object> summary(List<ShortageItemStore.Summary> rows) {
        Map<String, Map<String, Long>> lines = new LinkedHashMap<>();
        for (ShortageItemStore.Summary row : rows) {
            Map<String, Long> line = lines.computeIfAbsent(row.dimension().name(), k -> newLine());
            line.merge(row.state().name(), row.total(), Long::sum);
            if (row.state() == ShortageModel.ItemState.OPEN || row.state() == ShortageModel.ItemState.ACKNOWLEDGED) {
                line.merge("active", row.total(), Long::sum);
                line.merge(row.severity() == null ? "NO_SEVERITY" : row.severity().name(), row.total(), Long::sum);
            }
        }
        Map<String, Object> summary = new LinkedHashMap<>();
        lines.forEach((dimension, counts) -> summary.put(dimension, new LinkedHashMap<String, Object>(counts)));
        return summary;
    }

    private static Map<String, Long> newLine() {
        Map<String, Long> line = new LinkedHashMap<>();
        line.put("active", 0L);
        line.put("P1", 0L);
        line.put("P2", 0L);
        line.put("P3", 0L);
        line.put("P4", 0L);
        line.put("NO_SEVERITY", 0L);
        return line;
    }

    public static Map<String, Object> page(long total, int offset, int limit, List<?> records) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("total", total);
        m.put("offset", offset);
        m.put("limit", limit);
        m.put("records", new ArrayList<>(records));
        return m;
    }

    public List<Map<String, Object>> items(List<ShortageItem> list) {
        List<Map<String, Object>> rows = new ArrayList<>();
        list.forEach(i -> rows.add(item(i)));
        return rows;
    }

    public List<Map<String, Object>> alerts(List<ShortageAlert> list) {
        List<Map<String, Object>> rows = new ArrayList<>();
        list.forEach(a -> rows.add(alert(a)));
        return rows;
    }

    public List<Map<String, Object>> notifies(List<ShortageNotify> list, boolean withPayload) {
        List<Map<String, Object>> rows = new ArrayList<>();
        list.forEach(n -> rows.add(notify(n, withPayload)));
        return rows;
    }

    private Object parse(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        try {
            return json.readValue(text, Object.class);
        } catch (Exception e) {
            return text;
        }
    }

    static LocalDateTime parseTime(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        return LocalDateTime.parse(text);
    }

    static LocalDate parseDate(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        return LocalDate.parse(text);
    }

    static Severity parseSeverity(String text) {
        return text == null || text.isBlank() ? null : Severity.parse(text);
    }
}
