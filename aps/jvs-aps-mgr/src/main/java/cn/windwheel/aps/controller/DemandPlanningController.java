package cn.windwheel.aps.controller;

import cn.windwheel.aps.auth.AuthSessionKeys;
import cn.windwheel.aps.demand.ApsInventoryEvidence;
import cn.windwheel.aps.demand.PlanningInput;
import cn.windwheel.aps.demand.PlanningJobs;
import cn.windwheel.aps.demand.PlanningStore;
import cn.windwheel.common.annotation.RequirePermission;
import cn.windwheel.common.utils.AuthContextHolder;
import cn.windwheel.common.utils.R;

import lombok.RequiredArgsConstructor;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.Arrays;

import javax.servlet.http.HttpServletRequest;

/** Reconciled series imports and an at-least-once pull outbox for ERP connectors. */
@RestController
@RequestMapping("/demand-planning")
@RequiredArgsConstructor
@RequirePermission("aps:demand:view")
public class DemandPlanningController {
    private final cn.windwheel.aps.demand.LaunchReviewStore launchReviews;
    private final PlanningStore store;
    private final PlanningJobs jobs;
    private final ApsInventoryEvidence inventory;

    @Value("${aps.demand.legacy-source-zone:Asia/Shanghai}")
    private String legacyZone;

    @GetMapping("/aps-inventory/{material}")
    @RequirePermission("aps:material:view")
    public R<?> inventory(@PathVariable String material) {
        return R.ok(inventory.read(material, java.time.ZoneId.of(legacyZone)));
    }

    @Value("${aps.demand.jobs-enabled:false}")
    private boolean jobsEnabled;

    @Value("${aps.demand.approval-tenants:}")
    private String approvalTenants;

    @ModelAttribute
    public void authenticated(HttpServletRequest request) {
        if (!"session".equals(request.getAttribute(AuthSessionKeys.REQUEST_AUTH_CONTEXT_SOURCE)))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "需要已认证的登录会话");
    }

    @PostMapping("/launch-reviews")
    @RequirePermission("aps:demand:edit")
    public R<?> createLaunch(@RequestBody cn.windwheel.aps.demand.LaunchReview.Request request) {
        return R.ok(launchReviews.create(request));
    }

    @GetMapping("/launch-reviews")
    public R<?> launchList(
            @RequestParam String location,
            @RequestParam String material,
            @RequestParam(defaultValue = "0") int offset) {
        return R.ok(launchReviews.list(location, material, offset));
    }

    @GetMapping("/launch-reviews/{id}")
    public R<?> launch(@PathVariable String id) {
        return R.ok(launchReviews.get(id));
    }

    @PostMapping("/launch-reviews/{id}/rounds/{round}/votes")
    @RequirePermission("aps:demand:edit")
    public R<?> launchVote(
            @PathVariable String id,
            @PathVariable int round,
            @RequestBody cn.windwheel.aps.demand.LaunchReview.Vote vote) {
        launchReviews.vote(id, round, vote);
        return R.ok();
    }

    @PostMapping("/launch-reviews/{id}/{action}")
    @RequirePermission("aps:demand:edit")
    public R<?> launchAction(
            @PathVariable String id,
            @PathVariable String action,
            @RequestBody cn.windwheel.aps.demand.LaunchReviewStore.Command command) {
        switch (action) {
            case "close" -> launchReviews.close(id, command);
            case "next" -> launchReviews.next(id, command);
            case "draft" -> {
                return R.ok(launchReviews.draft(id, command));
            }
            default -> throw new PlanningStore.Missing();
        }
        return R.ok();
    }

    @GetMapping("/series")
    public R<?> series(@RequestParam(defaultValue = "0") int offset) {
        return R.ok(store.series(offset));
    }

    @GetMapping("/series/status")
    public R<?> status(@RequestParam String location, @RequestParam String material) {
        return R.ok(store.status(location, material));
    }

    @PostMapping({"/snapshots", "/demand-events:import"})
    @RequirePermission("aps:demand:import")
    public R<?> ingest(@RequestBody PlanningInput input) {
        return R.ok(store.ingest(input));
    }

    @PostMapping("/planning-runs")
    @RequirePermission("aps:demand:run")
    public R<?> calculate(@RequestBody PlanningStore.RunRequest request) {
        return R.ok(store.calculate(request));
    }

    @PostMapping(value = "/planning-runs", params = "async=true")
    @RequirePermission("aps:demand:run")
    public ResponseEntity<?> enqueue(@RequestBody PlanningStore.RunRequest request) {
        if (!jobsEnabled) throw new PlanningStore.Conflict("异步作业尚未启用");
        return ResponseEntity.accepted().body(R.ok(jobs.enqueue(request)));
    }

    @GetMapping("/jobs/{id}")
    public R<?> job(@PathVariable String id) {
        return R.ok(jobs.status(id));
    }

    @GetMapping("/planning-runs/{id}")
    public R<?> run(@PathVariable String id) {
        return R.ok(store.run(id));
    }

    public record Page(int total, int offset, java.util.List<?> records) {}

    private Page page(java.util.List<?> rows, int offset, int limit) {
        if (offset < 0 || limit < 1 || limit > 500) throw new IllegalArgumentException("分页范围无效");
        int start = Math.min(offset, rows.size());
        return new Page(
                rows.size(), offset, rows.subList(start, Math.min(start + limit, rows.size())));
    }

    @GetMapping({"/forecasts", "/inventory-projections", "/supply-proposals"})
    public R<?> dataset(
            @RequestParam String runId,
            @RequestParam(defaultValue = "0") int offset,
            @RequestParam(defaultValue = "100") int limit,
            HttpServletRequest request) {
        var r = store.run(runId).result();
        var records =
                switch (request.getServletPath()
                        .substring(request.getServletPath().lastIndexOf('/') + 1)) {
                    case "forecasts" -> r.forecasts();
                    case "inventory-projections" -> r.projection();
                    case "supply-proposals" -> r.proposals();
                    default -> throw new PlanningStore.Missing();
                };
        return R.ok(page(records, offset, limit));
    }

    @GetMapping("/planning-runs/{id}/input")
    public R<?> evidence(@PathVariable String id) {
        return R.ok(store.evidence(id));
    }

    @PostMapping("/forecasts/drafts")
    @RequirePermission("aps:demand:edit")
    public R<?> draft(@RequestBody PlanningStore.OverrideRequest request) {
        return R.ok(store.draft(request));
    }

    @GetMapping("/forecasts/drafts")
    public R<?> drafts(
            @RequestParam String location,
            @RequestParam String material,
            @RequestParam(defaultValue = "0") int offset) {
        return R.ok(store.drafts(location, material, offset));
    }

    @PostMapping("/forecasts/drafts/{id}/approve")
    @RequirePermission("aps:demand:override")
    public R<?> publish(@PathVariable String id) {
        store.publishDraft(id);
        return R.ok();
    }

    @PutMapping("/forecasts/override")
    @RequirePermission("aps:demand:override")
    public R<?> override(@RequestBody PlanningStore.OverrideRequest override) {
        return R.ok(store.override(override));
    }

    @PostMapping("/supply-proposals/approve")
    @RequirePermission("aps:demand:approve")
    public R<?> approve(@RequestBody PlanningStore.Approval approval) {
        if (Arrays.stream(approvalTenants.split(","))
                .map(String::trim)
                .noneMatch(AuthContextHolder.getTenantId()::equals))
            throw new PlanningStore.Conflict("当前租户处于影子运行阶段");
        return R.ok(store.approve(approval));
    }

    @GetMapping("/purchases")
    public R<?> purchases(
            @RequestParam String state, @RequestParam(defaultValue = "0") int offset) {
        return R.ok(store.purchases(state, offset));
    }

    @PostMapping("/supply-receipts/{id}")
    @RequirePermission("aps:demand:receipt")
    public R<?> acknowledge(@PathVariable String id, @RequestBody Receipt receipt) {
        store.acknowledge(id, receipt.externalLine());
        return R.ok();
    }

    @PostMapping("/supply-receipts/{id}/reject")
    @RequirePermission("aps:demand:receipt")
    public R<?> reject(@PathVariable String id, @RequestBody Receipt receipt) {
        store.rejectReceipt(id, receipt.externalLine(), receipt.reason());
        return R.ok();
    }

    public record Receipt(String externalLine, String reason) {}

    @ExceptionHandler({PlanningStore.Conflict.class, DuplicateKeyException.class})
    public ResponseEntity<?> conflict(RuntimeException e) {
        return ResponseEntity.status(409)
                .body(
                        R.failed(
                                409,
                                e instanceof DuplicateKeyException ? "外部行号已经绑定" : e.getMessage()));
    }

    @ExceptionHandler(PlanningStore.Missing.class)
    public ResponseEntity<?> missing() {
        return ResponseEntity.status(404).body(R.failed(404, "记录不存在"));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<?> invalid(IllegalArgumentException e) {
        return ResponseEntity.unprocessableEntity().body(R.failed(422, e.getMessage()));
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<?> forbidden(ResponseStatusException e) {
        return ResponseEntity.status(e.getStatus())
                .body(R.failed(e.getRawStatusCode(), e.getReason()));
    }
}
