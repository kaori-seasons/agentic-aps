package cn.windwheel.aps.shortage;

import cn.windwheel.aps.shortage.ShortageModel.Channel;
import cn.windwheel.aps.shortage.ShortageModel.NotifyState;
import cn.windwheel.aps.shortage.ShortageModel.Receipt;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * 推送队列。租约机制与需求作业队列同构：select for update skip locked 认领、lease_token 防串、
 * 租约过期后可被重新认领。多个节点同时轮询时各自认领不同的行，不会重复推送。
 */
@Repository
@RequiredArgsConstructor
public class ShortageNotifyStore {
    /** 只有这两个渠道需要主动投递；出站通道等外部来拉，台账在创建时即已送达。 */
    static final String PUSH_CHANNELS = "('WEBHOOK','EMAIL')";
    static final String COLUMNS = "tenant_id,id,alert_id,channel,target,idempotency_key,state,attempts,lease_token,"
            + "lease_until,available_at,last_attempt_at,sent_at,http_status,last_error,receipt_state,receipt_note,"
            + "receipt_by,receipt_at,payload,created_at";

    private final JdbcTemplate db;

    /** 同一渠道配多个接收端时，(alert, channel) 会撞键，所以唯一键要带上接收端的摘要。 */
    static String idempotencyKey(String alertId, Channel channel, String target) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(target.getBytes(StandardCharsets.UTF_8));
            return alertId + ":" + channel.name() + ":" + HexFormat.of().formatHex(digest).substring(0, 32);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    public void insert(ShortageNotify n) {
        db.update("insert into jvs_shortage_notify (" + COLUMNS + ") values ("
                        + ShortageItemStore.placeholders(COLUMNS.split(",").length) + ")",
                n.getTenantId(), n.getId(), n.getAlertId(), n.getChannel().name(), n.getTarget(),
                n.getIdempotencyKey(), n.getState().name(), n.getAttempts(), n.getLeaseToken(), n.getLeaseUntil(),
                n.getAvailableAt(), n.getLastAttemptAt(), n.getSentAt(), n.getHttpStatus(), n.getLastError(),
                n.getReceiptState() == null ? null : n.getReceiptState().name(), n.getReceiptNote(), n.getReceiptBy(),
                n.getReceiptAt(), n.getPayload(), n.getCreatedAt());
    }

    static ShortageNotify map(ResultSet r) throws SQLException {
        return new ShortageNotify()
                .setTenantId(r.getString("tenant_id"))
                .setId(r.getString("id"))
                .setAlertId(r.getString("alert_id"))
                .setChannel(Channel.valueOf(r.getString("channel")))
                .setTarget(r.getString("target"))
                .setIdempotencyKey(r.getString("idempotency_key"))
                .setState(NotifyState.valueOf(r.getString("state")))
                .setAttempts(r.getInt("attempts"))
                .setLeaseToken(r.getString("lease_token"))
                .setLeaseUntil(r.getObject("lease_until", LocalDateTime.class))
                .setAvailableAt(r.getObject("available_at", LocalDateTime.class))
                .setLastAttemptAt(r.getObject("last_attempt_at", LocalDateTime.class))
                .setSentAt(r.getObject("sent_at", LocalDateTime.class))
                .setHttpStatus(r.getObject("http_status") == null ? null : r.getInt("http_status"))
                .setLastError(r.getString("last_error"))
                .setReceiptState(r.getString("receipt_state") == null ? null : Receipt.valueOf(r.getString("receipt_state")))
                .setReceiptNote(r.getString("receipt_note"))
                .setReceiptBy(r.getString("receipt_by"))
                .setReceiptAt(r.getObject("receipt_at", LocalDateTime.class))
                .setPayload(r.getString("payload"))
                .setCreatedAt(r.getObject("created_at", LocalDateTime.class));
    }

    public Optional<ShortageNotify> find(String tenant, String id) {
        return db.query("select " + COLUMNS + " from jvs_shortage_notify where tenant_id=? and id=?",
                (r, i) -> map(r), tenant, id).stream().findFirst();
    }

    public List<ShortageNotify> byAlert(String tenant, String alertId) {
        return db.query("select " + COLUMNS + " from jvs_shortage_notify where tenant_id=? and alert_id=?"
                + " order by created_at,id", (r, i) -> map(r), tenant, alertId);
    }

    public List<String> tenantsWithDue(LocalDateTime now) {
        return db.queryForList("select distinct tenant_id from jvs_shortage_notify where channel in " + PUSH_CHANNELS
                + " and ((state='PENDING' and available_at<=?) or (state='SENDING' and lease_until<?))",
                String.class, now, now);
    }

    /**
     * 认领到期的推送并计一次尝试。发送中途崩溃留下的行在租约过期后会被这里重新认领，结果未知的那次也算一次失败。
     */
    @Transactional
    public List<ShortageNotify> claim(String tenant, LocalDateTime now, int leaseSeconds, int limit) {
        List<ShortageNotify> due = db.query("select " + COLUMNS + " from jvs_shortage_notify where tenant_id=?"
                        + " and channel in " + PUSH_CHANNELS
                        + " and ((state='PENDING' and available_at<=?) or (state='SENDING' and lease_until<?))"
                        + " order by available_at,created_at,id limit ? for update skip locked",
                (r, i) -> map(r), tenant, now, now, limit);
        for (ShortageNotify n : due) {
            boolean reclaimed = n.getState() == NotifyState.SENDING;
            String token = UUID.randomUUID().toString();
            db.update("update jvs_shortage_notify set state='SENDING',attempts=attempts+1,lease_token=?,lease_until=?,"
                            + " last_attempt_at=?,last_error=? where tenant_id=? and id=?",
                    token, now.plusSeconds(leaseSeconds), now,
                    reclaimed ? "上次投递租约过期，结果未知，按失败重试" : null, tenant, n.getId());
            n.setState(NotifyState.SENDING).setLeaseToken(token).setAttempts(n.getAttempts() + 1)
                    .setLastAttemptAt(now);
        }
        return due;
    }

    /** 后续写操作都带着租约令牌：行若已被别的节点重新认领，这里的更新就不会生效。 */
    public void sent(String tenant, String id, String leaseToken, LocalDateTime at, int httpStatus) {
        db.update("update jvs_shortage_notify set state='SENT',sent_at=?,http_status=?,last_error=null,"
                        + " lease_token=null,lease_until=null where tenant_id=? and id=? and lease_token=?",
                at, httpStatus, tenant, id, leaseToken);
    }

    public void failed(String tenant, String id, String leaseToken, LocalDateTime now, Integer httpStatus,
                       String error, LocalDateTime retryAt) {
        db.update("update jvs_shortage_notify set state=?,available_at=?,http_status=?,last_error=?,lease_token=null,"
                        + " lease_until=null where tenant_id=? and id=? and lease_token=?",
                retryAt == null ? NotifyState.DEAD.name() : NotifyState.PENDING.name(),
                retryAt == null ? now : retryAt, httpStatus, error, tenant, id, leaseToken);
    }

    /**
     * 预警进入终态时取消还没投出的推送：已经解决的问题不该再被推出去。
     * 正在投递中的行一并作废，租约令牌随之清掉，投递线程回来时它的结果不会被采纳。
     */
    public void cancelPending(String tenant, List<String> alertIds, String reason) {
        if (alertIds.isEmpty()) {
            return;
        }
        List<Object> args = new ArrayList<>(List.of(ShortageText.trim(reason, 500), tenant));
        args.addAll(alertIds);
        db.update("update jvs_shortage_notify set state='SKIPPED',last_error=?,lease_token=null,lease_until=null"
                        + " where tenant_id=? and state in ('PENDING','SENDING') and alert_id in ("
                        + ShortageItemStore.placeholders(alertIds.size()) + ")", args.toArray());
    }

    public void skipped(String tenant, String id, String reason) {
        db.update("update jvs_shortage_notify set state='SKIPPED',last_error=?,lease_token=null,lease_until=null"
                + " where tenant_id=? and id=?", reason, tenant, id);
    }

    /** 触发限流的条目稍后再试，不丢。 */
    public void defer(String tenant, List<String> ids, String leaseToken, LocalDateTime until) {
        if (ids.isEmpty()) {
            return;
        }
        List<Object> args = new ArrayList<>(List.of(until, leaseToken, tenant));
        args.addAll(ids);
        // 限流不算一次失败尝试，把认领时加上的那次计数退回去，否则排队也会把重试次数耗光。
        db.update("update jvs_shortage_notify set state='PENDING',available_at=?,attempts=greatest(attempts-1,0),"
                        + " lease_token=null,lease_until=null"
                        + " where tenant_id=? and lease_token=? and id in (" + ShortageItemStore.placeholders(ids.size()) + ")",
                args.toArray());
    }

    /** 最近一分钟每个接收端已投递与在途的条数，限流按它算。 */
    public Map<String, Integer> recentPerTarget(String tenant, Channel channel, LocalDateTime since) {
        Map<String, Integer> result = new LinkedHashMap<>();
        db.query("select target,count(*) from jvs_shortage_notify where tenant_id=? and channel=?"
                        + " and state in ('SENDING','SENT') and coalesce(sent_at,last_attempt_at)>=? group by target",
                (org.springframework.jdbc.core.RowCallbackHandler) r -> result.put(r.getString(1), r.getInt(2)),
                tenant, channel.name(), since);
        return result;
    }

    /** 回执落在推送记录上，作为闭环证据。没有回执记录的预警不算完成闭环。 */
    public void receipt(String tenant, List<String> alertIds, Receipt receipt, String note, String by,
                        LocalDateTime at) {
        if (alertIds.isEmpty()) {
            return;
        }
        // 备注可以为空，List.of 不接受 null 元素。
        List<Object> args = new ArrayList<>(java.util.Arrays.asList(receipt.name(), note, by, at, tenant));
        args.addAll(alertIds);
        db.update("update jvs_shortage_notify set receipt_state=?,receipt_note=?,receipt_by=?,receipt_at=?"
                        + " where tenant_id=? and alert_id in (" + ShortageItemStore.placeholders(alertIds.size()) + ")",
                args.toArray());
    }

    /** 拉取出站的回执：确认收到即视为送达，拒收则是终态并记原因。 */
    public void deliveryReceipt(String tenant, String id, boolean accepted, String note, String by, LocalDateTime at) {
        db.update("update jvs_shortage_notify set state=?,sent_at=?,receipt_state=?,receipt_note=?,receipt_by=?,"
                        + " receipt_at=?,last_error=? where tenant_id=? and id=? and channel='OUTBOX'",
                accepted ? NotifyState.SENT.name() : NotifyState.DEAD.name(), accepted ? at : null,
                accepted ? Receipt.ACK.name() : Receipt.REJECTED.name(), note, by, at,
                accepted ? null : "外部系统拒收：" + note, tenant, id);
    }

    public record Page(long total, List<ShortageNotify> records) {}

    /** 拉取不改状态，回执才改状态，避免拉取后崩溃导致条目丢失。 */
    public Page outbox(String tenant, NotifyState state, int offset, int limit) {
        Long total = db.queryForObject("select count(*) from jvs_shortage_notify where tenant_id=? and channel='OUTBOX'"
                + " and state=?", Long.class, tenant, state.name());
        List<ShortageNotify> records = db.query("select " + COLUMNS + " from jvs_shortage_notify where tenant_id=?"
                        + " and channel='OUTBOX' and state=? order by available_at,created_at,id limit ? offset ?",
                (r, i) -> map(r), tenant, state.name(), limit, offset);
        return new Page(total == null ? 0 : total, records);
    }
}
