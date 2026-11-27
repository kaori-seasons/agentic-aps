package cn.windwheel.aps.shortage;

import cn.windwheel.aps.shortage.ShortageModel.AlertState;
import cn.windwheel.aps.shortage.ShortageModel.Dimension;
import cn.windwheel.aps.shortage.ShortageModel.Severity;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 预警台账。去重键是 (alertKey, severity) 的唯一索引：同一时间桶、同一等级不重复建台账，
 * 并发复评撞到同一键时后到的一方走更新而不是报错，天然适合多节点。
 */
@Repository
@RequiredArgsConstructor
public class ShortageAlertStore {
    private final JdbcTemplate db;

    static final String[] COLUMNS = {
            "tenant_id", "alert_id", "alert_key", "shortage_key", "rule_code", "dimension", "entity_key", "bucket",
            "severity", "title", "payload", "state", "observed_count", "first_observed_at", "last_observed_at",
            "last_pushed_at", "pushed_count", "acknowledged_by", "acknowledged_at", "resolution_note",
            "created_at", "updated_at"};
    private static final String SELECT = "select " + String.join(",", COLUMNS) + " from jvs_shortage_alert";

    private static final String INSERT = "insert into jvs_shortage_alert (" + String.join(",", COLUMNS) + ") values ("
            + ShortageItemStore.placeholders(COLUMNS.length) + ") on duplicate key update"
            + " observed_count=observed_count+1,last_observed_at=values(last_observed_at),updated_at=values(updated_at)";

    /**
     * 插入一条预警；若同键同等级已存在则只累加观测次数。
     *
     * @return true 表示新建，false 表示撞上去重键
     */
    public boolean insertOrTouch(ShortageAlert a) {
        return db.update(INSERT, a.getTenantId(), a.getAlertId(), a.getAlertKey(), a.getShortageKey(),
                a.getRule().name(), a.getDimension().name(), a.getEntityKey(), a.getBucket(), a.getSeverity().name(),
                a.getTitle(), a.getPayload(), a.getState().name(), a.getObservedCount(), a.getFirstObservedAt(),
                a.getLastObservedAt(), a.getLastPushedAt(), a.getPushedCount(), a.getAcknowledgedBy(),
                a.getAcknowledgedAt(), a.getResolutionNote(), a.getCreatedAt(), a.getUpdatedAt()) == 1;
    }

    static ShortageAlert map(ResultSet r) throws SQLException {
        return new ShortageAlert()
                .setTenantId(r.getString("tenant_id"))
                .setAlertId(r.getString("alert_id"))
                .setAlertKey(r.getString("alert_key"))
                .setShortageKey(r.getString("shortage_key"))
                .setRule(ShortageRule.parse(r.getString("rule_code")))
                .setDimension(Dimension.valueOf(r.getString("dimension")))
                .setEntityKey(r.getString("entity_key"))
                .setBucket(r.getString("bucket"))
                .setSeverity(Severity.valueOf(r.getString("severity")))
                .setTitle(r.getString("title"))
                .setPayload(r.getString("payload"))
                .setState(AlertState.valueOf(r.getString("state")))
                .setObservedCount(r.getInt("observed_count"))
                .setFirstObservedAt(r.getObject("first_observed_at", LocalDateTime.class))
                .setLastObservedAt(r.getObject("last_observed_at", LocalDateTime.class))
                .setLastPushedAt(r.getObject("last_pushed_at", LocalDateTime.class))
                .setPushedCount(r.getInt("pushed_count"))
                .setAcknowledgedBy(r.getString("acknowledged_by"))
                .setAcknowledgedAt(r.getObject("acknowledged_at", LocalDateTime.class))
                .setResolutionNote(r.getString("resolution_note"))
                .setCreatedAt(r.getObject("created_at", LocalDateTime.class))
                .setUpdatedAt(r.getObject("updated_at", LocalDateTime.class));
    }

    public Optional<ShortageAlert> find(String tenant, String alertId) {
        return db.query(SELECT + " where tenant_id=? and alert_id=?", (r, i) -> map(r), tenant, alertId)
                .stream().findFirst();
    }

    /** 回执只带 alertId，租户要从预警本身反查。alertId 是系统签发的随机 UUID，外部无法构造。 */
    public Optional<ShortageAlert> findByAlertId(String alertId) {
        return db.query(SELECT + " where alert_id=?", (r, i) -> map(r), alertId).stream().findFirst();
    }

    public Optional<ShortageAlert> findByKey(String tenant, String alertKey, Severity severity) {
        return db.query(SELECT + " where tenant_id=? and alert_key=? and severity=?",
                (r, i) -> map(r), tenant, alertKey, severity.name()).stream().findFirst();
    }

    /** 欠料条目下所有未终结的预警。 */
    public List<ShortageAlert> openForItems(String tenant, List<String> keys) {
        if (keys.isEmpty()) {
            return List.of();
        }
        List<Object> args = new ArrayList<>();
        args.add(tenant);
        args.addAll(keys);
        return db.query(SELECT + " where tenant_id=? and shortage_key in ("
                        + ShortageItemStore.placeholders(keys.size()) + ") and state in ('OPEN','ACKNOWLEDGED')",
                (r, i) -> map(r), args.toArray());
    }

    public List<ShortageAlert> openForEntity(String tenant, ShortageRule rule, String entityKey) {
        return db.query(SELECT + " where tenant_id=? and rule_code=? and entity_key=?"
                        + " and state in ('OPEN','ACKNOWLEDGED')", (r, i) -> map(r), tenant, rule.name(), entityKey);
    }

    public List<ShortageAlert> nonTerminalForItems(String tenant, List<String> keys) {
        if (keys.isEmpty()) {
            return List.of();
        }
        List<Object> args = new ArrayList<>();
        args.add(tenant);
        args.addAll(keys);
        return db.query(SELECT + " where tenant_id=? and shortage_key in ("
                        + ShortageItemStore.placeholders(keys.size()) + ") and state in ('OPEN','ACKNOWLEDGED','MUTED')",
                (r, i) -> map(r), args.toArray());
    }

    public void touch(String tenant, String alertId, LocalDateTime at) {
        db.update("update jvs_shortage_alert set observed_count=observed_count+1,last_observed_at=?,updated_at=?"
                + " where tenant_id=? and alert_id=?", at, at, tenant, alertId);
    }

    public void markPushed(String tenant, String alertId, LocalDateTime at) {
        db.update("update jvs_shortage_alert set pushed_count=pushed_count+1,last_pushed_at=?,updated_at=?"
                + " where tenant_id=? and alert_id=?", at, at, tenant, alertId);
    }

    public void transition(String tenant, List<String> alertIds, AlertState state, String by, LocalDateTime at,
                           String note) {
        if (alertIds.isEmpty()) {
            return;
        }
        List<Object> args = new ArrayList<>();
        args.add(state.name());
        if (state == AlertState.ACKNOWLEDGED) {
            args.add(by);
            args.add(at);
        } else {
            args.add(null);
            args.add(null);
        }
        args.add(note);
        args.add(at);
        args.add(tenant);
        args.addAll(alertIds);
        db.update("update jvs_shortage_alert set state=?,acknowledged_by=?,acknowledged_at=?,resolution_note=?,"
                + " updated_at=? where tenant_id=? and alert_id in ("
                + ShortageItemStore.placeholders(alertIds.size()) + ") and state in ('OPEN','ACKNOWLEDGED')",
                args.toArray());
    }

    /** 条目下是否还有未确认的预警，决定条目在回执后该停在什么状态。 */
    public long countOpen(String tenant, String shortageKey) {
        Long total = db.queryForObject("select count(*) from jvs_shortage_alert where tenant_id=? and shortage_key=?"
                + " and state='OPEN'", Long.class, tenant, shortageKey);
        return total == null ? 0 : total;
    }

    public record Page(long total, List<ShortageAlert> records) {}

    public Page list(String tenant, Dimension dimension, Severity severity, AlertState state,
                     ShortageRule rule, LocalDateTime since, int offset, int limit) {
        StringBuilder where = new StringBuilder(" where tenant_id=?");
        List<Object> args = new ArrayList<>(List.of(tenant));
        if (dimension != null) {
            where.append(" and dimension=?");
            args.add(dimension.name());
        }
        if (severity != null) {
            where.append(" and severity=?");
            args.add(severity.name());
        }
        if (state != null) {
            where.append(" and state=?");
            args.add(state.name());
        }
        if (rule != null) {
            where.append(" and rule_code=?");
            args.add(rule.name());
        }
        if (since != null) {
            where.append(" and created_at>=?");
            args.add(since);
        }
        Long total = db.queryForObject("select count(*) from jvs_shortage_alert" + where, Long.class, args.toArray());
        List<Object> page = new ArrayList<>(args);
        page.add(limit);
        page.add(offset);
        // 等级高的在前，同级里新的在前。
        List<ShortageAlert> records = db.query(SELECT + where + " order by field(severity,'P1','P2','P3','P4'),"
                + " created_at desc, alert_id limit ? offset ?", (r, i) -> map(r), page.toArray());
        return new Page(total == null ? 0 : total, records);
    }
}
