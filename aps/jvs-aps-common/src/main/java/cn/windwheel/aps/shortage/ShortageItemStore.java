package cn.windwheel.aps.shortage;

import cn.windwheel.aps.shortage.ShortageModel.Dimension;
import cn.windwheel.aps.shortage.ShortageModel.ItemState;
import cn.windwheel.aps.shortage.ShortageModel.Severity;
import cn.windwheel.aps.shortage.ShortageModel.Source;
import cn.windwheel.aps.shortage.ShortageModel.SupplyMode;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * 欠料清单落库。按业务自然键幂等：同一运行重复落库只刷新观测值，不累加观测次数；
 * 不同运行视为新一轮观测，上一轮未再观测到的条目自动关闭。
 */
@Repository
@RequiredArgsConstructor
public class ShortageItemStore {
    private final JdbcTemplate db;

    static final String[] COLUMNS = {
            "tenant_id", "shortage_key", "dimension", "source", "run_id", "detected_at", "first_detected_at",
            "data_cutoff", "severity", "reason_code", "material_id", "material_code", "material_name", "unit",
            "gap_quantity", "required_quantity", "required_at", "week_start", "order_code", "location_code",
            "reference", "source_reason", "on_hand_available", "on_hand_consumed", "in_transit_consumed",
            "in_transit_skipped", "skipped_lines", "available_in_transit", "supply_mode", "expected_eta",
            "supplier_code", "earliest_start", "delay_duration", "downstream_tasks", "safety_stock",
            "scenario_balance", "detail", "state", "observed_count", "last_evaluated_at", "resolved_by",
            "resolved_at", "resolution_note", "created_at", "updated_at"};
    private static final String SELECT = "select " + String.join(",", COLUMNS) + " from jvs_shortage_item";

    private static final String UPSERT = "insert into jvs_shortage_item (" + String.join(",", COLUMNS) + ") values ("
            + placeholders(COLUMNS.length) + ") on duplicate key update"
            // MySQL 的赋值从左到右生效，后面的表达式看到的是前面已更新的值，
            // 所以依赖旧 run_id 与旧 state 的列必须排在它们之前。
            + " observed_count=case when state='RESOLVED' then 1 when run_id=values(run_id) then observed_count"
            + " else observed_count+1 end,"
            + " first_detected_at=if(state='RESOLVED',values(first_detected_at),first_detected_at),"
            + " resolved_by=if(state='RESOLVED',null,resolved_by),"
            + " resolved_at=if(state='RESOLVED',null,resolved_at),"
            + " resolution_note=if(state='RESOLVED',null,resolution_note),"
            + " state=if(state='RESOLVED','OPEN',state),"
            + " run_id=values(run_id),detected_at=values(detected_at),data_cutoff=values(data_cutoff),"
            + " severity=values(severity),reason_code=values(reason_code),material_id=values(material_id),"
            + " material_name=values(material_name),unit=values(unit),gap_quantity=values(gap_quantity),"
            + " required_quantity=values(required_quantity),required_at=values(required_at),"
            + " week_start=values(week_start),location_code=values(location_code),reference=values(reference),"
            + " source_reason=values(source_reason),on_hand_available=values(on_hand_available),"
            + " on_hand_consumed=values(on_hand_consumed),in_transit_consumed=values(in_transit_consumed),"
            + " in_transit_skipped=values(in_transit_skipped),skipped_lines=values(skipped_lines),"
            + " available_in_transit=values(available_in_transit),supply_mode=values(supply_mode),"
            + " expected_eta=values(expected_eta),supplier_code=values(supplier_code),"
            // 排程关联字段属于上一轮求解，新一轮观测先清空，等求解结束再回填。
            + " earliest_start=values(earliest_start),delay_duration=values(delay_duration),"
            + " downstream_tasks=values(downstream_tasks),safety_stock=values(safety_stock),"
            + " scenario_balance=values(scenario_balance),detail=values(detail),"
            + " last_evaluated_at=values(last_evaluated_at),updated_at=values(updated_at)";

    private Object[] params(ShortageItem i) {
        return new Object[]{
                i.getTenantId(), i.getShortageKey(), i.getDimension().name(), i.getSource().name(), i.getRunId(),
                i.getDetectedAt(), i.getFirstDetectedAt(), i.getDataCutoff(), name(i.getSeverity()), i.getReasonCode(),
                i.getMaterialId(), i.getMaterialCode(), i.getMaterialName(), i.getUnit(), i.getGapQuantity(),
                i.getRequiredQuantity(), i.getRequiredAt(), i.getWeekStart(), i.getOrderCode(), i.getLocationCode(),
                i.getReference(), i.getSourceReason(), i.getOnHandAvailable(), i.getOnHandConsumed(),
                i.getInTransitConsumed(), i.getInTransitSkipped(), i.getSkippedLines(), i.getAvailableInTransit(),
                name(i.getSupplyMode()), i.getExpectedEta(), i.getSupplierCode(), i.getEarliestStart(),
                i.getDelayDuration(), i.getDownstreamTasks(), i.getSafetyStock(), i.getScenarioBalance(),
                i.getDetail(), i.getState().name(), i.getObservedCount(), i.getLastEvaluatedAt(),
                i.getResolvedBy(), i.getResolvedAt(), i.getResolutionNote(), i.getCreatedAt(), i.getUpdatedAt()};
    }

    private static String name(Enum<?> value) {
        return value == null ? null : value.name();
    }

    public void upsert(ShortageItem item) {
        db.update(UPSERT, params(item));
    }

    static ShortageItem map(ResultSet r) throws SQLException {
        return new ShortageItem()
                .setTenantId(r.getString("tenant_id"))
                .setShortageKey(r.getString("shortage_key"))
                .setDimension(Dimension.valueOf(r.getString("dimension")))
                .setSource(Source.valueOf(r.getString("source")))
                .setRunId(r.getString("run_id"))
                .setDetectedAt(r.getObject("detected_at", LocalDateTime.class))
                .setFirstDetectedAt(r.getObject("first_detected_at", LocalDateTime.class))
                .setDataCutoff(r.getObject("data_cutoff", LocalDateTime.class))
                .setSeverity(r.getString("severity") == null ? null : Severity.valueOf(r.getString("severity")))
                .setReasonCode(r.getString("reason_code"))
                .setMaterialId(r.getString("material_id"))
                .setMaterialCode(r.getString("material_code"))
                .setMaterialName(r.getString("material_name"))
                .setUnit(r.getString("unit"))
                .setGapQuantity(r.getBigDecimal("gap_quantity"))
                .setRequiredQuantity(r.getBigDecimal("required_quantity"))
                .setRequiredAt(r.getObject("required_at", LocalDateTime.class))
                .setWeekStart(r.getObject("week_start", LocalDate.class))
                .setOrderCode(r.getString("order_code"))
                .setLocationCode(r.getString("location_code"))
                .setReference(r.getString("reference"))
                .setSourceReason(r.getString("source_reason"))
                .setOnHandAvailable(r.getBigDecimal("on_hand_available"))
                .setOnHandConsumed(zero(r.getBigDecimal("on_hand_consumed")))
                .setInTransitConsumed(zero(r.getBigDecimal("in_transit_consumed")))
                .setInTransitSkipped(zero(r.getBigDecimal("in_transit_skipped")))
                .setSkippedLines(r.getString("skipped_lines"))
                .setAvailableInTransit(r.getString("available_in_transit"))
                .setSupplyMode(r.getString("supply_mode") == null ? null : SupplyMode.valueOf(r.getString("supply_mode")))
                .setExpectedEta(r.getObject("expected_eta", LocalDateTime.class))
                .setSupplierCode(r.getString("supplier_code"))
                .setEarliestStart(r.getObject("earliest_start", LocalDateTime.class))
                .setDelayDuration(r.getString("delay_duration"))
                .setDownstreamTasks(r.getString("downstream_tasks"))
                .setSafetyStock(r.getBigDecimal("safety_stock"))
                .setScenarioBalance(r.getBigDecimal("scenario_balance"))
                .setDetail(r.getString("detail"))
                .setState(ItemState.valueOf(r.getString("state")))
                .setObservedCount(r.getInt("observed_count"))
                .setLastEvaluatedAt(r.getObject("last_evaluated_at", LocalDateTime.class))
                .setResolvedBy(r.getString("resolved_by"))
                .setResolvedAt(r.getObject("resolved_at", LocalDateTime.class))
                .setResolutionNote(r.getString("resolution_note"))
                .setCreatedAt(r.getObject("created_at", LocalDateTime.class))
                .setUpdatedAt(r.getObject("updated_at", LocalDateTime.class));
    }

    private static BigDecimal zero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    public Optional<ShortageItem> find(String tenant, String shortageKey) {
        return db.query(SELECT + " where tenant_id=? and shortage_key=?", (r, i) -> map(r), tenant, shortageKey)
                .stream().findFirst();
    }

    public List<ShortageItem> findByRun(String tenant, String runId) {
        return db.query(SELECT + " where tenant_id=? and run_id=?", (r, i) -> map(r), tenant, runId);
    }

    /**
     * 待复评的条目。静默的条目也要复评：P1 不允许静默，等级升到 P1 时要自动解除静默。
     *
     * @param before 上次复评不晚于该时刻的才入选，让同一轮扫描不反复处理同一条
     */
    public List<ShortageItem> forSweep(String tenant, LocalDateTime before, int limit) {
        return db.query(SELECT + " where tenant_id=? and state in ('OPEN','ACKNOWLEDGED','MUTED')"
                        + " and (last_evaluated_at is null or last_evaluated_at<=?)"
                        // 最久没复评的排前面：条目数超过一批时，下一轮自然轮到这一轮没轮到的。
                        + " order by last_evaluated_at, field(severity,'P1','P2','P3','P4'), required_at, shortage_key"
                        + " limit ?",
                (r, i) -> map(r), tenant, before, limit);
    }

    private record Filter(String where, List<Object> args) {}

    /**
     * @param anyState true 表示不限状态。缺省只看未关闭的条目，已解决的历史不混进当前缺料板。
     */
    private Filter filter(String tenant, Dimension dimension, Severity severity, ItemState state, boolean anyState,
                          String orderCode, String materialCode) {
        List<Object> args = new ArrayList<>();
        StringBuilder sql = new StringBuilder(" where tenant_id=?");
        args.add(tenant);
        if (dimension != null) {
            sql.append(" and dimension=?");
            args.add(dimension.name());
        }
        if (severity != null) {
            sql.append(" and severity=?");
            args.add(severity.name());
        }
        if (state != null) {
            sql.append(" and state=?");
            args.add(state.name());
        } else if (!anyState) {
            // 默认只看需要人处理的条目。静默是人工明确“不再推送、不再打扰”的状态，
            // 不该继续留在默认列表里；要看它得显式带上 includeClosed=true。
            sql.append(" and state in ('OPEN','ACKNOWLEDGED')");
        }
        if (orderCode != null && !orderCode.isBlank()) {
            sql.append(" and order_code=?");
            args.add(orderCode.trim());
        }
        if (materialCode != null && !materialCode.isBlank()) {
            sql.append(" and material_code=?");
            args.add(materialCode.trim());
        }
        return new Filter(sql.toString(), args);
    }

    public List<ShortageItem> list(String tenant, Dimension dimension, Severity severity, ItemState state,
                                   boolean anyState, String orderCode, String materialCode, int offset, int limit) {
        Filter f = filter(tenant, dimension, severity, state, anyState, orderCode, materialCode);
        List<Object> args = new ArrayList<>(f.args());
        args.add(limit);
        args.add(offset);
        // 两条线分开排，齐套线在前；等级高的在前，没有等级的排在最后。
        return db.query(SELECT + f.where() + " order by field(dimension,'ORDER','WEEK'),"
                + " field(ifnull(severity,'P9'),'P1','P2','P3','P4','P9'), required_at, week_start, material_code,"
                + " shortage_key limit ? offset ?", (r, i) -> map(r), args.toArray());
    }

    public long count(String tenant, Dimension dimension, Severity severity, ItemState state, boolean anyState,
                      String orderCode, String materialCode) {
        Filter f = filter(tenant, dimension, severity, state, anyState, orderCode, materialCode);
        Long total = db.queryForObject("select count(*) from jvs_shortage_item" + f.where(), Long.class,
                f.args().toArray());
        return total == null ? 0 : total;
    }

    public List<ShortageItem> byKeys(String tenant, List<String> keys) {
        if (keys.isEmpty()) {
            return List.of();
        }
        List<Object> args = new ArrayList<>();
        args.add(tenant);
        args.addAll(keys);
        return db.query(SELECT + " where tenant_id=? and shortage_key in (" + placeholders(keys.size()) + ")",
                (r, i) -> map(r), args.toArray());
    }

    /** 分维度、状态、等级的条数，界面按线分组与折叠水位线要靠它。 */
    public List<Summary> summary(String tenant) {
        return db.query("select dimension,state,severity,count(*) as total from jvs_shortage_item where tenant_id=?"
                        + " group by dimension,state,severity",
                (r, i) -> new Summary(Dimension.valueOf(r.getString(1)), ItemState.valueOf(r.getString(2)),
                        r.getString(3) == null ? null : Severity.valueOf(r.getString(3)), r.getLong(4)), tenant);
    }

    public record Summary(Dimension dimension, ItemState state, Severity severity, long total) {}

    public void updateEvaluation(String tenant, String shortageKey, Severity severity, String reasonCode,
                                 ItemState state, LocalDateTime at) {
        db.update("update jvs_shortage_item set severity=?,reason_code=?,state=?,last_evaluated_at=?,updated_at=?"
                        + " where tenant_id=? and shortage_key=?",
                name(severity), reasonCode, state.name(), at, at, tenant, shortageKey);
    }

    public void attachSchedule(String tenant, String shortageKey, LocalDateTime earliestStart, String delayDuration,
                               String downstreamTasks, LocalDateTime at) {
        db.update("update jvs_shortage_item set earliest_start=?,delay_duration=?,downstream_tasks=?,updated_at=?"
                        + " where tenant_id=? and shortage_key=?",
                earliestStart, delayDuration, downstreamTasks, at, tenant, shortageKey);
    }

    /** 有待复评条目的租户。周期任务没有请求上下文，租户只能从数据里来。 */
    public List<String> activeTenants() {
        return db.queryForList("select distinct tenant_id from jvs_shortage_item where state in"
                + " ('OPEN','ACKNOWLEDGED','MUTED')", String.class);
    }

    /**
     * 新一轮运行没有再观测到的条目自动关闭。静默的条目保持静默，不替人改他明确设过的状态。
     *
     * @return 被关闭的条目键
     */
    public List<String> resolveUnobserved(String tenant, Dimension dimension, String runId, String locationCode,
                                          String materialCode, LocalDateTime at) {
        List<Object> args = new ArrayList<>(List.of(tenant, dimension.name(), runId));
        StringBuilder sql = new StringBuilder("select shortage_key from jvs_shortage_item where tenant_id=?"
                + " and dimension=? and run_id<>? and state in ('OPEN','ACKNOWLEDGED')");
        if (locationCode != null) {
            sql.append(" and location_code=?");
            args.add(locationCode);
        }
        if (materialCode != null) {
            sql.append(" and material_code=?");
            args.add(materialCode);
        }
        List<String> keys = db.queryForList(sql.toString(), String.class, args.toArray());
        if (!keys.isEmpty()) {
            List<Object> update = new ArrayList<>(List.of(ShortageModel.SYSTEM_ACTOR, at,
                    "运行 " + runId + " 未再观测到缺口", at, at, tenant));
            update.addAll(keys);
            db.update("update jvs_shortage_item set state='RESOLVED',resolved_by=?,resolved_at=?,resolution_note=?,"
                            + " severity=null,last_evaluated_at=?,updated_at=? where tenant_id=?"
                            + " and shortage_key in (" + placeholders(keys.size()) + ")", update.toArray());
        }
        return keys;
    }

    /** 已解决与已静默记录处理人、时刻与说明；未确认与已确认只是流转，处理字段保持为空。 */
    public void transition(String tenant, String shortageKey, ItemState state, String by, LocalDateTime at, String note) {
        boolean closed = state == ItemState.RESOLVED || state == ItemState.MUTED;
        db.update("update jvs_shortage_item set state=?,resolved_by=?,resolved_at=?,resolution_note=?,last_evaluated_at=?,"
                        + " updated_at=? where tenant_id=? and shortage_key=?",
                state.name(), closed ? by : null, closed ? at : null, closed ? note : null, at, at, tenant, shortageKey);
    }

    /** 数据质量规则要数的对象：未关闭且依据的数据已过期的条目。 */
    public long countStale(String tenant, Dimension dimension, LocalDateTime cutoff) {
        Long total = db.queryForObject("select count(*) from jvs_shortage_item where tenant_id=? and dimension=?"
                + " and state in ('OPEN','ACKNOWLEDGED') and data_cutoff<?", Long.class, tenant, dimension.name(), cutoff);
        return total == null ? 0 : total;
    }

    public long countUnattributed(String tenant) {
        Long total = db.queryForObject("select count(*) from jvs_shortage_item where tenant_id=? and dimension='ORDER'"
                + " and (order_code is null or order_code='')", Long.class, tenant);
        return total == null ? 0 : total;
    }

    static String placeholders(int count) {
        return String.join(",", Collections.nCopies(count, "?"));
    }
}
