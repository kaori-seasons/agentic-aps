package cn.windwheel.aps.shortage;

import cn.windwheel.aps.shortage.ShortageModel.Dimension;
import cn.windwheel.aps.shortage.ShortageModel.Severity;
import cn.windwheel.aps.shortage.ShortageRule.Effective;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 数据质量规则。它们不挂在任何欠料条目上，是系统级预警。
 * <p>
 * DATA_SNAPSHOT_EXPIRED 与 DATA_GAP_UNATTRIBUTABLE 的数据源是欠料清单自身，现在就能评。
 * DATA_STALE 与 SYNC_DEAD_LETTER 依赖外部同步通道的批次表与死信表，那两张表随接入方案一期落地。
 * 通道没上线时这里如实记“数据源不可用”并跳过，绝不按已支持口径给出结论。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ShortageDataQuality {
    /** 接入方案 4.4.2、4.4.3、4.4.1 的实体类型名。 */
    static final List<String> WATERMARK_ENTITIES = List.of("INVENTORY", "SUPPLY_LINE", "SALES_ORDER");
    static final String DEAD_LETTER_TABLE = "jvs_sync_dead_letter";
    static final String SYNC_BATCH_TABLE = "jvs_sync_batch";

    private final JdbcTemplate db;
    private final ShortageItemStore items;
    private final ShortageAlertService alertService;

    /** 每条规则这次是怎么处置的。手工扫描时原样返回，让运维看见哪些规则因数据源缺失被跳过。 */
    public record Report(Map<String, String> notes) {}

    public Report evaluate(String tenant, LocalDateTime now, ShortageAlertService.Outcome outcome) {
        Map<String, String> notes = new LinkedHashMap<>();
        snapshotExpired(tenant, Dimension.ORDER, now, outcome, notes);
        snapshotExpired(tenant, Dimension.WEEK, now, outcome, notes);
        unattributed(tenant, now, outcome, notes);
        deadLetters(tenant, now, outcome, notes);
        staleWatermark(tenant, now, outcome, notes);
        return new Report(notes);
    }

    /** 齐套或水位所依据的数据超过最大时效，仍没有新的运行来刷新。 */
    private void snapshotExpired(String tenant, Dimension dimension, LocalDateTime now,
                                 ShortageAlertService.Outcome outcome, Map<String, String> notes) {
        Effective rule = alertService.effective(tenant, ShortageRule.DATA_SNAPSHOT_EXPIRED);
        String key = rule.rule().name() + ":" + dimension.name();
        if (!rule.enabled() || rule.thresholdMinutes() == null) {
            notes.put(key, "已禁用");
            return;
        }
        long count = items.countStale(tenant, dimension, now.minusMinutes(rule.thresholdMinutes()));
        if (count == 0) {
            alertService.clearSystem(tenant, rule.rule(), "SNAPSHOT:" + dimension.name(), now, "欠料依据已刷新");
            notes.put(key, "正常");
            return;
        }
        long hours = rule.thresholdMinutes() / 60;
        Map<String, Object> facts = new LinkedHashMap<>();
        facts.put("dimension", dimension.name());
        facts.put("staleItems", count);
        facts.put("maxAgeHours", hours);
        boolean created = alertService.raiseSystem(tenant, rule.rule(), "SNAPSHOT:" + dimension.name(),
                rule.severity(), dimension + " 线有 " + count + " 条未闭合欠料依据的数据已超过 " + hours + " 小时未刷新",
                facts, now);
        notes.put(key, "过期 " + count + " 条");
        count(created, outcome);
    }

    private void unattributed(String tenant, LocalDateTime now, ShortageAlertService.Outcome outcome,
                              Map<String, String> notes) {
        Effective rule = alertService.effective(tenant, ShortageRule.DATA_GAP_UNATTRIBUTABLE);
        if (!rule.enabled()) {
            notes.put(rule.rule().name(), "已禁用");
            return;
        }
        long count = items.countUnattributed(tenant);
        if (count == 0) {
            alertService.clearSystem(tenant, rule.rule(), "UNATTRIBUTED", now, "欠料归属已完整");
            notes.put(rule.rule().name(), "正常");
            return;
        }
        Map<String, Object> facts = new LinkedHashMap<>();
        facts.put("unattributedItems", count);
        boolean created = alertService.raiseSystem(tenant, rule.rule(), "UNATTRIBUTED", rule.severity(),
                "有 " + count + " 条欠料清单条目没有订单归属", facts, now);
        notes.put(rule.rule().name(), "缺归属 " + count + " 条");
        count(created, outcome);
    }

    private void deadLetters(String tenant, LocalDateTime now, ShortageAlertService.Outcome outcome,
                             Map<String, String> notes) {
        ShortageRule rule = ShortageRule.SYNC_DEAD_LETTER;
        Effective effective = alertService.effective(tenant, rule);
        if (!effective.enabled()) {
            notes.put(rule.name(), "已禁用");
            return;
        }
        if (!exists(DEAD_LETTER_TABLE)) {
            notes.put(rule.name(), "跳过：同步通道尚未落地，死信表不存在");
            return;
        }
        // 时长一律用库端时钟算：TIMESTAMP 列经会话时区换算后与 JVM 时间可能相差数小时。
        Map<String, Object> row = db.queryForMap("select count(*) as total,"
                + " timestampdiff(second,min(created_at),current_timestamp) as oldest_seconds"
                + " from jvs_sync_dead_letter where tenant_id=? and resolved_at is null", tenant);
        long total = number(row.get("total"));
        if (total == 0) {
            alertService.clearSystem(tenant, rule, "DEAD_LETTER", now, "死信已清空");
            notes.put(rule.name(), "正常");
            return;
        }
        long minutes = number(row.get("oldest_seconds")) / 60;
        // 超 30 分钟升 P2，取规则等级与 P2 中更高的那个。
        Severity severity = effective.severity();
        if (minutes >= (effective.thresholdMinutes() == null ? 0 : effective.thresholdMinutes())) {
            severity = severity.atLeast(Severity.P2) ? severity : Severity.P2;
        }
        Map<String, Object> facts = new LinkedHashMap<>();
        facts.put("deadLetters", total);
        facts.put("oldestMinutes", minutes);
        boolean created = alertService.raiseSystem(tenant, rule, "DEAD_LETTER", severity,
                "同步死信 " + total + " 条未处理，最早一条已等待 " + minutes + " 分钟", facts, now);
        notes.put(rule.name(), "死信 " + total + " 条，最早 " + minutes + " 分钟");
        count(created, outcome);
    }

    /**
     * 水位延迟。方案按数据类别给了三个窗口（库存 15 分钟、在途 4 小时、订单实时），
     * 在同步通道落地、分类水位可取之后再按类拆分；在此之前只按规则的单一阈值评最长可容忍延迟。
     */
    private void staleWatermark(String tenant, LocalDateTime now, ShortageAlertService.Outcome outcome,
                                Map<String, String> notes) {
        ShortageRule rule = ShortageRule.DATA_STALE;
        Effective effective = alertService.effective(tenant, rule);
        if (!effective.enabled()) {
            notes.put(rule.name(), "已禁用");
            return;
        }
        if (!exists(SYNC_BATCH_TABLE)) {
            notes.put(rule.name(), "跳过：同步通道尚未落地，批次表不存在");
            return;
        }
        List<Map<String, Object>> rows = db.queryForList("select entity_type,"
                        + " timestampdiff(second,max(as_of),current_timestamp) as lag_seconds"
                        + " from jvs_sync_batch where tenant_id=? and state='ACCEPTED' and entity_type in (?,?,?)"
                        + " group by entity_type",
                tenant, WATERMARK_ENTITIES.get(0), WATERMARK_ENTITIES.get(1), WATERMARK_ENTITIES.get(2));
        if (rows.isEmpty()) {
            notes.put(rule.name(), "跳过：尚无已接受的同步批次");
            return;
        }
        long limit = (effective.thresholdMinutes() == null ? 0 : effective.thresholdMinutes()) * 60;
        List<String> stale = new ArrayList<>();
        Map<String, Object> lags = new LinkedHashMap<>();
        for (Map<String, Object> r : rows) {
            long minutes = number(r.get("lag_seconds")) / 60;
            lags.put(String.valueOf(r.get("entity_type")), minutes);
            if (number(r.get("lag_seconds")) > limit) {
                stale.add(r.get("entity_type") + " " + minutes + " 分钟");
            }
        }
        if (stale.isEmpty()) {
            alertService.clearSystem(tenant, rule, "WATERMARK", now, "数据水位已恢复");
            notes.put(rule.name(), "正常");
            return;
        }
        Map<String, Object> facts = new LinkedHashMap<>();
        facts.put("lagMinutes", lags);
        facts.put("maxLagMinutes", limit / 60);
        boolean created = alertService.raiseSystem(tenant, rule, "WATERMARK", effective.severity(),
                "数据同步水位延迟超过 " + limit / 60 + " 分钟：" + String.join("、", stale), facts, now);
        notes.put(rule.name(), "延迟：" + String.join("、", stale));
        count(created, outcome);
    }

    private boolean exists(String table) {
        Integer count = db.queryForObject("select count(*) from information_schema.tables"
                + " where table_schema=database() and table_name=?", Integer.class, table);
        return count != null && count > 0;
    }

    private static long number(Object value) {
        return value instanceof Number n ? n.longValue() : 0L;
    }

    private static void count(boolean created, ShortageAlertService.Outcome outcome) {
        if (created) {
            outcome.created();
        } else {
            outcome.repeated();
        }
    }
}
