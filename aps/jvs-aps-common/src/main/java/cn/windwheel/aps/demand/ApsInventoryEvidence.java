package cn.windwheel.aps.demand;

import cn.windwheel.aps.solve.util.DurationUtils;
import cn.windwheel.common.utils.AuthContextHolder;

import lombok.RequiredArgsConstructor;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.*;
import java.util.List;

/**
 * Material-wide legacy balance. Mapping this to exactly one reconciled location is an importer
 * decision.
 */
@Service
@RequiredArgsConstructor
public class ApsInventoryEvidence {
    private final JdbcTemplate db;

    public record Incoming(
            String id, String orderCode, BigDecimal sourceQuantity, Instant availableAt) {}

    public record Image(
            String material,
            String unit,
            String source,
            BigDecimal onHand,
            BigDecimal safety,
            Instant readAt,
            List<Incoming> processing) {}

    @Transactional(
            readOnly = true,
            isolation = org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public Image read(String id, ZoneId sourceZone) {
        PlanningChecks.key(id);
        String tenant = AuthContextHolder.getTenantId();
        var rows =
                db.queryForList(
                        "select code,unit,source,quantity,safety_stock,buffer_time from"
                                + " jvs_material where tenant_id=? and id=? and del_flag=0",
                        tenant,
                        id);
        if (rows.isEmpty()) throw new PlanningStore.Missing();
        var row = rows.get(0);
        Duration buffer = DurationUtils.convertDuration((String) row.get("buffer_time"));
        PlanningChecks.require(buffer != null && !buffer.isNegative(), "物料缓冲期不可解析");
        var incoming =
                db.query(
                        "select id,code,quantity,delivery_time from jvs_incoming_material_order"
                            + " where tenant_id=? and material_code=? and order_status='PROCESSING'"
                            + " and del_flag=0 order by delivery_time,id",
                        (r, i) -> {
                            var due = r.getObject(4, LocalDateTime.class);
                            PlanningChecks.require(due != null, "进行中的来料缺少预计到货时间");
                            return new Incoming(
                                    r.getString(1),
                                    r.getString(2),
                                    r.getBigDecimal(3),
                                    due.atZone(sourceZone).plus(buffer).toInstant());
                        },
                        tenant,
                        row.get("code"));
        return new Image(
                id,
                (String) row.get("unit"),
                (String) row.get("source"),
                (BigDecimal) row.get("quantity"),
                (BigDecimal) row.get("safety_stock"),
                Instant.now(),
                incoming);
    }
}
