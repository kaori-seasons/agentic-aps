package cn.windwheel.aps.demand;

import static cn.windwheel.aps.demand.PlanningChecks.*;

import cn.windwheel.common.utils.AuthContextHolder;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;

@Service
@RequiredArgsConstructor
@Transactional
public class PlanningStore {
    private final JdbcTemplate db;
    private final ObjectMapper json;
    private final PlanningJobs jobs;

    @org.springframework.beans.factory.annotation.Value("${aps.demand.jobs-enabled:false}")
    private boolean jobsEnabled;

    @org.springframework.beans.factory.annotation.Value("${aps.demand.auto-plan:false}")
    private boolean autoPlan;

    @org.springframework.beans.factory.annotation.Value("${aps.demand.max-approval-age-hours:24}")
    private long maxApprovalAgeHours;

    /** 补货运行到缺料预警的挂接点。字段注入是为了不改本类的构造器，需求模块的既有装配保持原样。 */
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private cn.windwheel.aps.shortage.ShortageDemandHook shortageHook;

    private static final String SCOPE = " tenant_id=? and location_id=? and material_id=? ";

    public record Scope(String location, String material) {}

    public record Head(long revision, long generation, String currentRun) {}

    public record RunRequest(
            String location, String material, long revision, long generation, String requestKey) {}

    public record OverrideRequest(
            String location,
            String material,
            LocalDate week,
            BigDecimal quantity,
            Instant expiresAt,
            String reason,
            long generation) {}

    public record Approval(String runId, int index, String reason, BigDecimal quantity) {}

    public record Run(
            String id,
            String location,
            String material,
            long revision,
            long generation,
            DemandPlanner.Result result) {}

    public static class Conflict extends RuntimeException {
        public Conflict(String message) {
            super(message);
        }
    }

    public static class Missing extends RuntimeException {
        public Missing() {
            super("记录不存在");
        }
    }

    private static String tenant() {
        return Objects.requireNonNull(AuthContextHolder.getTenantId());
    }

    private static String actor() {
        return Objects.requireNonNull(AuthContextHolder.getUserId());
    }

    private String encode(Object o) {
        try {
            return json.writeValueAsString(o);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("无法保存计划", e);
        }
    }

    private <T> T decode(String value, Class<T> type) {
        try {
            return json.readValue(value, type);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("无法读取计划", e);
        }
    }

    private Head head(String l, String m, boolean lock) {
        key(l);
        key(m);
        return db
                .query(
                        "select revision,generation,current_run from jvs_demand_series where "
                                + SCOPE
                                + (lock ? " for update" : ""),
                        (r, i) -> new Head(r.getLong(1), r.getLong(2), r.getString(3)),
                        tenant(),
                        l,
                        m)
                .stream()
                .findFirst()
                .orElseThrow(Missing::new);
    }

    public Head status(String l, String m) {
        return head(l, m, false);
    }

    private void invalidate(String l, String m) {
        db.update(
                "update jvs_demand_series set generation=generation+1,current_run=null where "
                        + SCOPE,
                tenant(),
                l,
                m);
    }

    private PlanningInput snapshot(String l, String m, long revision) {
        String data =
                db.queryForObject(
                        "select payload from jvs_demand_snapshot where "
                                + SCOPE
                                + " and revision=?",
                        String.class,
                        tenant(),
                        l,
                        m,
                        revision);
        return decode(data, PlanningInput.class);
    }

    public Head ingest(PlanningInput s) {
        validate(s);
        require(!s.asOf().isAfter(Instant.now()), "快照截点晚于当前时间");
        String l = s.location(), m = s.material(), payload = encode(s);
        String digest = cn.hutool.crypto.digest.DigestUtil.sha256Hex(payload);
        require(payload.length() <= 2_000_000, "快照过大，请按物料地点拆分");
        db.update(
                "insert into jvs_demand_series(tenant_id,location_id,material_id) values(?,?,?) on"
                        + " duplicate key update revision=revision",
                tenant(),
                l,
                m);
        Head head = head(l, m, true);
        if (s.revision() <= head.revision()) {
            var old =
                    db.queryForList(
                            "select input_digest from jvs_demand_snapshot where "
                                    + SCOPE
                                    + " and revision=?",
                            String.class,
                            tenant(),
                            l,
                            m,
                            s.revision());
            if (!old.isEmpty() && old.get(0).equals(digest)) return head;
            throw new Conflict("旧版本或同版本快照内容不一致");
        }
        if (head.revision() > 0) {
            PlanningInput previous = snapshot(l, m, head.revision());
            if (s.asOf().isBefore(previous.asOf())
                    || !s.unit().equals(previous.unit())
                    || !s.zone().equals(previous.zone())) throw new Conflict("截点倒退或库存计量单位、时区被更改");
            var nextLines =
                    s.supplies().stream()
                            .map(PlanningInput.Supply::line)
                            .collect(java.util.stream.Collectors.toSet());
            for (var old : previous.supplies()) {
                if (old.ordered().compareTo(old.received().add(old.cancelled())) > 0
                        && !nextLines.contains(old.line()))
                    throw new Conflict("旧在途行仍有余量，请提交到货或取消后的终态：" + old.line());
            }
            s = s.withSales(mergeSales(previous, s));
            validate(s);
            payload = encode(s);
            require(payload.length() <= 2_000_000, "合并后的序列超出单次发布规模");
        }
        db.update(
                "insert into"
                    + " jvs_demand_snapshot(tenant_id,location_id,material_id,revision,payload,input_digest,imported_by)"
                    + " values(?,?,?,?,?,?,?)",
                tenant(),
                l,
                m,
                s.revision(),
                payload,
                digest,
                actor());
        db.update(
                "update jvs_demand_series set revision=?,generation=generation+1,current_run=null"
                        + " where "
                        + SCOPE,
                s.revision(),
                tenant(),
                l,
                m);
        // Confirmation is counted solely through the newly reconciled inventory image.
        Map<String, PlanningInput.Supply> supplyByLine = new HashMap<>();
        s.supplies().forEach(po -> supplyByLine.put(po.line(), po));
        var awaiting =
                db.queryForList(
                        "select id,external_line,quantity from jvs_demand_purchase where "
                                + SCOPE
                                + " and state='ACKNOWLEDGED' for update",
                        tenant(),
                        l,
                        m);
        for (var approval : awaiting) {
            var po = supplyByLine.get(approval.get("external_line"));
            if (po == null) continue;
            if (po.ordered().compareTo((BigDecimal) approval.get("quantity")) != 0)
                throw new Conflict("已确认采购行数量与快照不符，请对账");
            db.update(
                    "update jvs_demand_purchase set state='RECONCILED' where tenant_id=? and id=?",
                    tenant(),
                    approval.get("id"));
        }
        return scheduleIfEnabled(l, m);
    }

    private Head scheduleIfEnabled(String l, String m) {
        Head h = head(l, m, false);
        if (autoPlan && jobsEnabled)
            jobs.enqueue(
                    new RunRequest(l, m, h.revision(), h.generation(), "auto-" + h.generation()));
        return h;
    }

    private static List<PlanningInput.Sale> mergeSales(PlanningInput previous, PlanningInput next) {
        Map<String, TreeMap<Long, PlanningInput.Sale>> ledger = new TreeMap<>();
        for (var row : previous.sales())
            ledger.computeIfAbsent(row.line(), k -> new TreeMap<>()).put(row.revision(), row);
        for (var row : next.sales()) {
            var old =
                    ledger.computeIfAbsent(row.line(), k -> new TreeMap<>())
                            .putIfAbsent(row.revision(), row);
            if (old != null
                    && (!old.due().equals(row.due())
                            || !old.knownAt().equals(row.knownAt())
                            || old.ordered().compareTo(row.ordered()) != 0
                            || old.shipped().compareTo(row.shipped()) != 0))
                throw new Conflict("同一销售行修订包含不同内容：" + row.line());
        }
        var rows = new ArrayList<PlanningInput.Sale>();
        var historyBoundary = next.historyStart().atStartOfDay(ZoneId.of(next.zone())).toInstant();
        for (var revisions : ledger.values()) {
            var last = revisions.lastEntry().getValue();
            if (last.due().isBefore(historyBoundary)
                    && last.ordered().compareTo(last.shipped()) == 0
                    && revisions.values().stream()
                            .allMatch(row -> row.due().isBefore(historyBoundary))) continue;
            rows.addAll(revisions.values());
        }
        return rows;
    }

    public PlanningInput evidence(String runId) {
        var run = run(runId);
        return snapshot(run.location(), run.material(), run.revision());
    }

    public String draft(OverrideRequest o) {
        var h = head(o.location(), o.material(), true);
        if (h.generation() != o.generation()) throw new Conflict("输入版本已更新");
        validateAdjustment(o);
        String id = UUID.randomUUID().toString();
        db.update(
                "insert into"
                    + " jvs_demand_adjustment(tenant_id,id,location_id,material_id,generation,week_start,quantity,expires_at,reason,submitted_by,state)"
                    + " values(?,?,?,?,?,?,?,?,?,?,'PENDING')",
                tenant(),
                id,
                o.location(),
                o.material(),
                o.generation(),
                java.sql.Date.valueOf(o.week()),
                o.quantity(),
                o.expiresAt().toString(),
                o.reason(),
                actor());
        return id;
    }

    public List<Map<String, Object>> drafts(String l, String m, int offset) {
        require(offset >= 0, "无效分页");
        return db.queryForList(
                "select id,generation,week_start,quantity,expires_at,reason,submitted_by from"
                        + " jvs_demand_adjustment where "
                        + SCOPE
                        + " and state='PENDING' and generation=(select generation from"
                        + " jvs_demand_series where "
                        + SCOPE
                        + ") "
                        + "order by created_at,id limit 50 offset ?",
                tenant(),
                l,
                m,
                tenant(),
                l,
                m,
                offset);
    }

    public void publishDraft(String id) {
        var list =
                db.queryForList(
                        "select * from jvs_demand_adjustment where tenant_id=? and id=? for update",
                        tenant(),
                        id);
        if (list.isEmpty()) throw new Missing();
        var r = list.get(0);
        if ("APPROVED".equals(r.get("state"))) return;
        if (actor().equals(r.get("submitted_by"))) throw new Conflict("预测覆盖需由另一名有权限的人员复核");
        override(
                new OverrideRequest(
                        (String) r.get("location_id"),
                        (String) r.get("material_id"),
                        ((java.sql.Date) r.get("week_start")).toLocalDate(),
                        (BigDecimal) r.get("quantity"),
                        Instant.parse((String) r.get("expires_at")),
                        (String) r.get("reason"),
                        ((Number) r.get("generation")).longValue()));
        db.update(
                "update jvs_demand_adjustment set state='APPROVED',approved_by=? where tenant_id=?"
                        + " and id=?",
                actor(),
                tenant(),
                id);
    }

    private static void validateAdjustment(OverrideRequest o) {
        if (o.quantity() != null) quantity(o.quantity());
        reason(o.reason());
        require(
                o.week() != null
                        && o.week().getDayOfWeek() == DayOfWeek.MONDAY
                        && o.expiresAt() != null
                        && o.expiresAt().isAfter(Instant.now()),
                "覆盖起点须为周一，且有效期尚未结束");
    }

    public Head override(OverrideRequest o) {
        Head h = head(o.location(), o.material(), true);
        if (h.generation() != o.generation()) throw new Conflict("预测版本已变更");
        validateAdjustment(o);
        db.update(
                "insert into"
                    + " jvs_demand_override(tenant_id,location_id,material_id,week_start,generation,quantity,reason,expires_at,approved_by)"
                    + " values(?,?,?,?,?,?,?,?,?)",
                tenant(),
                o.location(),
                o.material(),
                java.sql.Date.valueOf(o.week()),
                h.generation() + 1,
                o.quantity(),
                o.reason(),
                o.expiresAt().toString(),
                actor());
        invalidate(o.location(), o.material());
        return scheduleIfEnabled(o.location(), o.material());
    }

    private Map<LocalDate, DemandPlanner.Adjustment> overrides(
            String l, String m, LocalDate start, int weeks) {
        Map<LocalDate, DemandPlanner.Adjustment> map = new TreeMap<>();
        db.query(
                "select week_start,quantity,reason,expires_at from jvs_demand_override where "
                        + SCOPE
                        + " and week_start>=? and week_start<? order by generation",
                r -> {
                    LocalDate week = r.getDate(1).toLocalDate();
                    if (r.getBigDecimal(2) == null) map.remove(week);
                    else
                        map.put(
                                week,
                                new DemandPlanner.Adjustment(
                                        r.getBigDecimal(2),
                                        r.getString(3),
                                        Instant.parse(r.getString(4))));
                },
                tenant(),
                l,
                m,
                java.sql.Date.valueOf(start),
                java.sql.Date.valueOf(start.plusWeeks(weeks)));
        return map;
    }

    public Run calculate(RunRequest req) {
        String l = req.location(), m = req.material();
        key(req.requestKey());
        Head h = head(l, m, true);
        var existing =
                db.queryForList(
                        "select id from jvs_demand_run where " + SCOPE + " and request_key=?",
                        String.class,
                        tenant(),
                        l,
                        m,
                        req.requestKey());
        if (!existing.isEmpty()) {
            Run run = run(existing.get(0));
            if (run.generation() != req.generation() || run.revision() != req.revision())
                throw new Conflict("幂等键对应另一版本");
            return run;
        }
        if (h.revision() != req.revision() || h.generation() != req.generation())
            throw new Conflict("输入已变更，请刷新");
        PlanningInput s = snapshot(l, m, h.revision());
        var overrides =
                overrides(l, m, DemandPlanner.week(s.asOf(), ZoneId.of(s.zone())), s.weeks());
        var componentDemand = new ArrayList<PlanningInput.Requirement>();
        if (s.assembly() != null) {
            var parent = run(s.assembly().parentRun());
            require(parent.location().equals(l) && !parent.material().equals(m), "车型族与组件地点不同或自引用");
            var family = snapshot(l, parent.material(), parent.revision());
            require(
                    family.asOf().equals(s.asOf())
                            && family.zone().equals(s.zone())
                            && family.weeks() == s.weeks(),
                    "车型族和组件的截点、时区和预测范围须一致");
            require(
                    family.opening().signum() == 0
                            && family.supplies().isEmpty()
                            && family.requirements().isEmpty()
                            && family.assembly() == null,
                    "配置族须为独立需求，装配件库存应在配置层分配");
            componentDemand.addAll(s.assembly().requirements(parent.result(), m));
        }
        var previous =
                db.queryForList(
                        "select id from jvs_demand_run where "
                                + SCOPE
                                + " order by generation desc,created_at desc,id desc limit 1",
                        String.class,
                        tenant(),
                        l,
                        m);
        var carry =
                previous.isEmpty()
                        ? List.<DemandPlanner.Allocation>of()
                        : run(previous.get(0)).result().consumption();
        var result = DemandPlanner.calculate(s, overrides, componentDemand, carry);
        var outstanding =
                db.queryForList(
                        "select id from jvs_demand_purchase where "
                                + SCOPE
                                + " and state in ('READY','ACKNOWLEDGED')",
                        String.class,
                        tenant(),
                        l,
                        m);
        if (!outstanding.isEmpty())
            result = DemandPlanner.awaitingReconciliation(result, outstanding.get(0), s.asOf());
        String encodedResult = encode(result);
        require(
                encodedResult.getBytes(java.nio.charset.StandardCharsets.UTF_8).length
                        <= 12_000_000,
                "结果超过单序列上限，请缩短范围");
        String id = UUID.randomUUID().toString();
        db.update(
                "insert into"
                    + " jvs_demand_run(tenant_id,id,location_id,material_id,request_key,revision,generation,algorithm,result,overrides_used,created_by)"
                    + " values(?,?,?,?,?,?,?,'weekly-v1',?,?,?)",
                tenant(),
                id,
                l,
                m,
                req.requestKey(),
                h.revision(),
                h.generation(),
                encodedResult,
                encode(overrides),
                actor());
        db.update("update jvs_demand_series set current_run=? where " + SCOPE, id, tenant(), l, m);
        // 缺料预警只读本次计算的结果，挂在事务提交之后执行；挂接点自身失败不影响补货。
        if (shortageHook != null) {
            shortageHook.runCompleted(tenant(), id, l, m, s.unit(), result, s.asOf(), s.zone());
        }
        return new Run(id, l, m, h.revision(), h.generation(), result);
    }

    public Run run(String id) {
        return db
                .query(
                        "select location_id,material_id,revision,generation,result from"
                                + " jvs_demand_run where tenant_id=? and id=?",
                        (r, i) ->
                                new Run(
                                        id,
                                        r.getString(1),
                                        r.getString(2),
                                        r.getLong(3),
                                        r.getLong(4),
                                        decode(r.getString(5), DemandPlanner.Result.class)),
                        tenant(),
                        id)
                .stream()
                .findFirst()
                .orElseThrow(Missing::new);
    }

    public String approve(Approval a) {
        reason(a.reason());
        Run run = run(a.runId());
        Head h = head(run.location(), run.material(), true);
        var prior =
                db.queryForList(
                        "select id,quantity,reason from jvs_demand_purchase where tenant_id=? and"
                                + " run_id=? and proposal_index=? for update",
                        tenant(),
                        run.id(),
                        a.index());
        if (!prior.isEmpty()) {
            var row = prior.get(0);
            var requested =
                    a.quantity() != null
                            ? a.quantity()
                            : run.result().proposals().get(a.index()).quantity();
            if (!a.reason().equals(row.get("reason"))
                    || requested.compareTo((BigDecimal) row.get("quantity")) != 0)
                throw new Conflict("同一建议已有不同的审批内容");
            return (String) row.get("id");
        }
        if (h.currentRun() == null
                || h.revision() != run.revision()
                || h.generation() != run.generation()) throw new Conflict("计划已过期，请重算");
        if (run.result().exceptions().stream()
                .anyMatch(issue -> issue.reason().equals("STALE_ETA")))
            throw new Conflict("现有在途承诺已过期，请先核实交期或取消余量");
        require(a.index() >= 0 && a.index() < run.result().proposals().size(), "建议序号无效");
        if (a.index() != 0) throw new Conflict("请从最早采购窗口开始批准，后续窗口在回执对账后重算");
        var p = run.result().proposals().get(a.index());
        var s = snapshot(run.location(), run.material(), h.revision());
        BigDecimal amount = a.quantity() == null ? p.quantity() : a.quantity();
        quantity(amount);
        require(
                amount.signum() > 0
                        && amount.compareTo(s.policy().minimum()) >= 0
                        && amount.remainder(s.policy().multiple()).signum() == 0
                        && (s.policy().maximumOrder() == null
                                || amount.compareTo(s.policy().maximumOrder()) <= 0),
                "审批数量不满足起订量、包装倍数或采购上限");
        if (s.policy().purchaseBudget() != null)
            require(
                    amount.multiply(s.policy().unitCost()).compareTo(s.policy().purchaseBudget())
                            <= 0,
                    "批准数量超出可用采购预算");
        if (s.policy().maximumOnHand() != null)
            require(
                    run.result().projection().stream()
                            .filter(row -> !row.at().isBefore(p.availableAt()))
                            .noneMatch(
                                    row ->
                                            row.confirmed()
                                                            .add(amount)
                                                            .compareTo(s.policy().maximumOnHand())
                                                    > 0),
                    "批准数量超出库容");
        if (overrides(
                        run.location(),
                        run.material(),
                        DemandPlanner.week(s.asOf(), ZoneId.of(s.zone())),
                        s.weeks())
                .values()
                .stream()
                .anyMatch(
                        o ->
                                o.expiresAt().isAfter(s.asOf())
                                        && !o.expiresAt().isAfter(Instant.now())))
            throw new Conflict("运行使用的预测覆盖已到期，请更新快照");
        if (s.asOf().isBefore(Instant.now().minus(Duration.ofHours(maxApprovalAgeHours))))
            throw new Conflict("库存水位过期，请先完成库存对账");
        if (p.releaseAt().isBefore(Instant.now())) throw new Conflict("释放窗口已过，请更新快照及采购日历");
        var pending =
                db.queryForList(
                        "select id from jvs_demand_purchase where "
                                + SCOPE
                                + " and state in ('READY','ACKNOWLEDGED') limit 1 for update",
                        String.class,
                        tenant(),
                        run.location(),
                        run.material());
        if (!pending.isEmpty()) throw new Conflict("该物料地点尚有采购待对账");
        String id = UUID.randomUUID().toString();
        db.update(
                "insert into"
                    + " jvs_demand_purchase(tenant_id,id,run_id,proposal_index,location_id,material_id,supplier,unit,quantity,release_at,available_at,reason,approved_by,state)"
                    + " values(?,?,?,?,?,?,?,?,?,?,?,?,?,'READY')",
                tenant(),
                id,
                run.id(),
                a.index(),
                run.location(),
                run.material(),
                s.policy().supplier(),
                s.unit(),
                amount,
                p.releaseAt().toString(),
                p.availableAt().toString(),
                a.reason(),
                actor());
        invalidate(run.location(), run.material());
        return id;
    }

    private static void reason(String s) {
        require(s != null && !s.isBlank() && s.length() <= 500, "请输入1至500字的原因");
    }

    public List<Map<String, Object>> series(int offset) {
        require(offset >= 0, "无效分页");
        return db.queryForList(
                "select location_id,material_id,revision,generation,current_run from"
                    + " jvs_demand_series where tenant_id=? order by location_id,material_id limit"
                    + " 50 offset ?",
                tenant(),
                offset);
    }

    public List<Map<String, Object>> purchases(String state, int offset) {
        require(
                List.of("READY", "ACKNOWLEDGED", "RECONCILED", "REJECTED").contains(state)
                        && offset >= 0,
                "状态或分页无效");
        return db.queryForList(
                "select"
                    + " id,location_id,material_id,unit,supplier,quantity,release_at,available_at,run_id,external_line,state"
                    + " from jvs_demand_purchase where tenant_id=? and state=? order by"
                    + " created_at,id limit 50 offset ?",
                tenant(),
                state,
                offset);
    }

    public void rejectReceipt(String id, String external, String reason) {
        reason(reason);
        resolveReceipt(id, external, "REJECTED", reason);
    }

    public void acknowledge(String id, String external) {
        resolveReceipt(id, external, "ACKNOWLEDGED", null);
    }

    private void resolveReceipt(String id, String external, String state, String message) {
        key(external);
        var scopes =
                db.query(
                        "select location_id,material_id from jvs_demand_purchase where tenant_id=?"
                                + " and id=?",
                        (r, i) -> new Scope(r.getString(1), r.getString(2)),
                        tenant(),
                        id);
        if (scopes.isEmpty()) throw new Missing();
        Scope scope = scopes.get(0);
        head(scope.location(), scope.material(), true);
        var row =
                db.queryForMap(
                        "select state,external_line,receipt_note from jvs_demand_purchase where"
                                + " tenant_id=? and id=? for update",
                        tenant(),
                        id);
        if (external.equals(row.get("external_line"))
                && (state.equals(row.get("state"))
                        || ("ACKNOWLEDGED".equals(state) && "RECONCILED".equals(row.get("state"))))
                && Objects.equals(message, row.get("receipt_note"))) return;
        if (!"READY".equals(row.get("state"))) throw new Conflict("采购回执已绑定其他外部行");
        db.update(
                "update jvs_demand_purchase set state=?,external_line=?,receipt_note=? where"
                        + " tenant_id=? and id=?",
                state,
                external,
                message,
                tenant(),
                id);
        invalidate(scope.location(), scope.material());
    }
}
