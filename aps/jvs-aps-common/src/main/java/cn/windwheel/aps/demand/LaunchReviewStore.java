package cn.windwheel.aps.demand;

import static cn.windwheel.aps.demand.LaunchReview.*;
import static cn.windwheel.aps.demand.PlanningChecks.*;

import cn.windwheel.common.utils.AuthContextHolder;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.util.*;

@Service
@RequiredArgsConstructor
@Transactional
public class LaunchReviewStore {
    private final JdbcTemplate db;
    private final ObjectMapper json;
    private final PlanningStore planning;

    public record RoundView(
            int number,
            boolean closed,
            Vote ownVote,
            Summary summary,
            List<Vote> anonymousJudgments,
            String feedback) {}

    public record View(
            String id,
            Request request,
            Evidence evidence,
            boolean organizer,
            boolean expert,
            List<RoundView> rounds,
            String draftId) {}

    public record Command(int round, String feedback) {}

    private String tenant() {
        return Objects.requireNonNull(AuthContextHolder.getTenantId());
    }

    private String actor() {
        return Objects.requireNonNull(AuthContextHolder.getUserId());
    }

    private String encode(State state) {
        try {
            return json.writeValueAsString(state);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("评审序列化失败", e);
        }
    }

    private State read(String id) {
        key(id);
        var rows =
                db.queryForList(
                        "select payload from jvs_demand_launch_review where tenant_id=? and id=?"
                                + " for update",
                        tenant(),
                        id);
        if (rows.isEmpty()) throw new PlanningStore.Missing();
        try {
            State state = json.readValue((String) rows.get(0).get("payload"), State.class);
            if (!state.owner().equals(actor()) && !state.request().experts().contains(actor()))
                throw new PlanningStore.Missing();
            return state;
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("评审读取失败", e);
        }
    }

    private void save(String id, State state) {
        db.update(
                "update jvs_demand_launch_review set payload=? where tenant_id=? and id=?",
                encode(state),
                tenant(),
                id);
    }

    private void owner(State state) {
        if (!state.owner().equals(actor())) throw new PlanningStore.Conflict("仅组织者可操作");
    }

    private void active(State state) {
        if (state.draftId() != null) throw new PlanningStore.Conflict("评审已形成调整申请");
        if (!state.request().expiresAt().isAfter(Instant.now()))
            throw new PlanningStore.Conflict("评审已过期");
        var h = planning.status(state.request().location(), state.request().material());
        if (h.generation() != state.request().generation())
            throw new PlanningStore.Conflict("计划版本已变更，请重新评审");
    }

    public String create(Request r) {
        require(r != null, "评审内容缺失");
        key(r.location());
        key(r.material());
        require(r.week() != null && r.week().getDayOfWeek() == DayOfWeek.MONDAY, "需求周须为周一");
        require(r.expiresAt() != null && r.expiresAt().isAfter(Instant.now()), "有效期须在未来");
        text(r.context(), "产品定位、定价与上市背景");
        require(
                r.experts() != null && r.experts().size() >= 3 && r.experts().size() <= 20,
                "需要3至20位专家");
        r.experts().forEach(PlanningChecks::key);
        require(
                new HashSet<>(r.experts()).size() == r.experts().size()
                        && !r.experts().contains(actor()),
                "专家须唯一且与组织者独立");
        State state =
                new State(
                        r,
                        actor(),
                        evidence(r.analogs()),
                        List.of(new Round(1, false, Map.of(), "")),
                        null);
        active(state);
        var head = planning.status(r.location(), r.material());
        String payload =
                db.queryForObject(
                        "select payload from jvs_demand_snapshot where tenant_id=? and"
                            + " location_id=? and material_id=? and revision=?",
                        String.class,
                        tenant(),
                        r.location(),
                        r.material(),
                        head.revision());
        try {
            var input = json.readValue(payload, PlanningInput.class);
            var first =
                    input.asOf()
                            .atZone(ZoneId.of(input.zone()))
                            .toLocalDate()
                            .with(
                                    java.time.temporal.TemporalAdjusters.previousOrSame(
                                            DayOfWeek.MONDAY));
            require(
                    !r.week().isBefore(first) && r.week().isBefore(first.plusWeeks(input.weeks())),
                    "需求周须在当前快照的计划范围内");
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("快照读取失败", e);
        }
        String id = UUID.randomUUID().toString();
        db.update(
                "insert into jvs_demand_launch_review(tenant_id,id,location_id,material_id,payload)"
                        + " values(?,?,?,?,?)",
                tenant(),
                id,
                r.location(),
                r.material(),
                encode(state));
        return id;
    }

    public View get(String id) {
        State s = read(id);
        // Never return the expert roster, other individual estimates or their free-text rationale.
        Request safe =
                new Request(
                        s.request().location(),
                        s.request().material(),
                        s.request().week(),
                        s.request().generation(),
                        s.request().expiresAt(),
                        s.request().context(),
                        List.of(),
                        s.request().analogs());
        return new View(
                id,
                safe,
                s.evidence(),
                s.owner().equals(actor()),
                s.request().experts().contains(actor()),
                s.rounds().stream()
                        .map(
                                r ->
                                        new RoundView(
                                                r.number(),
                                                r.closed(),
                                                r.votes().get(actor()),
                                                r.closed() ? summarize(r.votes().values()) : null,
                                                r.closed()
                                                        ? r.votes().values().stream()
                                                                .sorted(
                                                                        Comparator.comparing(
                                                                                        Vote
                                                                                                ::expected)
                                                                                .thenComparing(
                                                                                        Vote
                                                                                                ::reason))
                                                                .toList()
                                                        : List.of(),
                                                r.feedback()))
                        .toList(),
                s.draftId());
    }

    public List<String> list(String location, String material, int offset) {
        key(location);
        key(material);
        require(offset >= 0, "分页无效");
        // Membership is filtered in SQL, before pagination.
        return db.queryForList(
                "select id from jvs_demand_launch_review where tenant_id=? and location_id=? and"
                        + " material_id=? and (json_unquote(json_extract(payload,'$.owner'))=? or"
                        + " json_contains(json_extract(payload,'$.request.experts'),json_quote(?)))"
                        + " order by created_at desc,id limit 50 offset ?",
                String.class,
                tenant(),
                location,
                material,
                actor(),
                actor(),
                offset);
    }

    public void vote(String id, int round, Vote vote) {
        State s = read(id);
        if (!s.request().experts().contains(actor())) throw new PlanningStore.Conflict("仅受邀专家可提交");
        active(s);
        Round r = s.rounds().get(s.rounds().size() - 1);
        if (r.closed() || r.number() != round) throw new PlanningStore.Conflict("轮次已关闭或变更");
        validate(vote);
        Vote old = r.votes().get(actor());
        if (old != null) {
            if (old.equals(vote)) return;
            throw new PlanningStore.Conflict("本轮已独立提交，请在下一轮修订");
        }
        var votes = new LinkedHashMap<>(r.votes());
        votes.put(actor(), vote);
        var rounds = new ArrayList<>(s.rounds());
        rounds.set(rounds.size() - 1, new Round(round, false, votes, ""));
        save(id, new State(s.request(), s.owner(), s.evidence(), rounds, null));
    }

    public void close(String id, Command c) {
        State s = read(id);
        owner(s);
        active(s);
        Round r = s.rounds().get(s.rounds().size() - 1);
        if (c.round() != r.number()) throw new PlanningStore.Conflict("轮次已变更");
        if (r.closed()) return;
        if (r.votes().size() != s.request().experts().size())
            throw new PlanningStore.Conflict("请等待全部专家独立提交");
        var rounds = new ArrayList<>(s.rounds());
        rounds.set(rounds.size() - 1, new Round(r.number(), true, r.votes(), ""));
        save(id, new State(s.request(), s.owner(), s.evidence(), rounds, null));
    }

    public void next(String id, Command c) {
        State s = read(id);
        owner(s);
        active(s);
        Round r = s.rounds().get(s.rounds().size() - 1);
        if (r.number() != c.round() || !r.closed()) throw new PlanningStore.Conflict("请先关闭当前轮次");
        require(r.number() < 5, "最多五轮，请形成结论或重新评审");
        text(c.feedback(), "下一轮共享事实");
        var rounds = new ArrayList<>(s.rounds());
        rounds.add(new Round(r.number() + 1, false, Map.of(), c.feedback()));
        save(id, new State(s.request(), s.owner(), s.evidence(), rounds, null));
    }

    public String draft(String id, Command c) {
        State s = read(id);
        owner(s);
        if (s.draftId() != null) return s.draftId();
        active(s);
        Round r = s.rounds().get(s.rounds().size() - 1);
        if (r.number() != c.round() || !r.closed()) throw new PlanningStore.Conflict("请先关闭当前轮次");
        text(c.feedback(), "决策依据");
        require(c.feedback().length() <= 300, "决策依据最多300字");
        var req = s.request();
        String draft =
                planning.draft(
                        new PlanningStore.OverrideRequest(
                                req.location(),
                                req.material(),
                                req.week(),
                                summarize(r.votes().values()).median(),
                                req.expiresAt(),
                                "新品评审 " + id + " 第" + r.number() + "轮中位数；" + c.feedback(),
                                req.generation()));
        save(id, new State(req, s.owner(), s.evidence(), s.rounds(), draft));
        return draft;
    }
}
