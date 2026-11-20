package cn.windwheel.aps.demand;

import static cn.windwheel.aps.demand.PlanningChecks.key;

import cn.windwheel.common.utils.AuthContextHolder;

import lombok.RequiredArgsConstructor;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
@RequiredArgsConstructor
public class PlanningJobs {
    private final JdbcTemplate db;

    public record Ticket(
            String tenant,
            String actor,
            String id,
            String token,
            int attempts,
            PlanningStore.RunRequest request) {}

    @Transactional
    public Map<String, Object> enqueue(PlanningStore.RunRequest req) {
        key(req.location());
        key(req.material());
        key(req.requestKey());
        String tenant = AuthContextHolder.getTenantId();
        db.update(
                "insert into"
                    + " jvs_demand_job(tenant_id,id,location_id,material_id,request_key,revision,generation,created_by,state)"
                    + " values(?,?,?,?,?,?,?,?,'QUEUED') on duplicate key update"
                    + " request_key=request_key",
                tenant,
                UUID.randomUUID().toString(),
                req.location(),
                req.material(),
                req.requestKey(),
                req.revision(),
                req.generation(),
                AuthContextHolder.getUserId());
        var rows =
                db.queryForMap(
                        "select id,revision,generation,state,run_id,detail from jvs_demand_job"
                                + " where tenant_id=? and location_id=? and material_id=? and"
                                + " request_key=?",
                        tenant,
                        req.location(),
                        req.material(),
                        req.requestKey());
        if (((Number) rows.get("revision")).longValue() != req.revision()
                || ((Number) rows.get("generation")).longValue() != req.generation())
            throw new PlanningStore.Conflict("作业幂等键已用于另一输入版本");
        return rows;
    }

    public Map<String, Object> status(String id) {
        return db
                .queryForList(
                        "select id,revision,generation,state,attempts,run_id,detail from"
                                + " jvs_demand_job where tenant_id=? and id=?",
                        AuthContextHolder.getTenantId(),
                        id)
                .stream()
                .findFirst()
                .orElseThrow(PlanningStore.Missing::new);
    }

    @Transactional
    public Optional<Ticket> claim() {
        var ticket =
                db
                        .query(
                                "select"
                                    + " tenant_id,created_by,id,attempts,location_id,material_id,revision,generation,request_key"
                                    + " from jvs_demand_job where state in ('QUEUED','RUNNING') and"
                                    + " available_at<=current_timestamp(6) order by"
                                    + " available_at,created_at,id limit 1 for update skip locked",
                                (r, i) ->
                                        new Ticket(
                                                r.getString(1),
                                                r.getString(2),
                                                r.getString(3),
                                                UUID.randomUUID().toString(),
                                                r.getInt(4) + 1,
                                                new PlanningStore.RunRequest(
                                                        r.getString(5),
                                                        r.getString(6),
                                                        r.getLong(7),
                                                        r.getLong(8),
                                                        r.getString(9))))
                        .stream()
                        .findFirst();
        ticket.ifPresent(
                t ->
                        db.update(
                                "update jvs_demand_job set"
                                    + " state='RUNNING',attempts=?,lease_token=?,available_at=date_add(current_timestamp(6),interval"
                                    + " 3 minute) where tenant_id=? and id=?",
                                t.attempts(),
                                t.token(),
                                t.tenant(),
                                t.id()));
        return ticket;
    }

    public void finish(Ticket t, String state, String run, String detail) {
        db.update(
                "update jvs_demand_job set"
                    + " state=?,run_id=?,detail=?,lease_token=null,available_at=date_add(current_timestamp(6),interval"
                    + " 10 second) where tenant_id=? and id=? and lease_token=?",
                state,
                run,
                detail,
                t.tenant(),
                t.id(),
                t.token());
    }
}
