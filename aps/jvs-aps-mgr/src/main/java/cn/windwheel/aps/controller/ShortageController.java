package cn.windwheel.aps.controller;

import cn.windwheel.aps.auth.AuthSessionKeys;
import cn.windwheel.aps.shortage.ShortageAlert;
import cn.windwheel.aps.shortage.ShortageAlertService;
import cn.windwheel.aps.shortage.ShortageAlertStore;
import cn.windwheel.aps.shortage.ShortageException;
import cn.windwheel.aps.shortage.ShortageItem;
import cn.windwheel.aps.shortage.ShortageItemStore;
import cn.windwheel.aps.shortage.ShortageModel.AlertState;
import cn.windwheel.aps.shortage.ShortageModel.Channel;
import cn.windwheel.aps.shortage.ShortageModel.Dimension;
import cn.windwheel.aps.shortage.ShortageModel.ItemState;
import cn.windwheel.aps.shortage.ShortageModel.NotifyState;
import cn.windwheel.aps.shortage.ShortageModel.Receipt;
import cn.windwheel.aps.shortage.ShortageModel.Severity;
import cn.windwheel.aps.shortage.ShortageNotify;
import cn.windwheel.aps.shortage.ShortageNotifyStore;
import cn.windwheel.aps.shortage.ShortageRule;
import cn.windwheel.aps.shortage.ShortageRuleStore;
import cn.windwheel.aps.shortage.ShortageScanService;
import cn.windwheel.aps.shortage.ShortageViews;
import cn.windwheel.common.annotation.AuditOperation;
import cn.windwheel.common.annotation.RequirePermission;
import cn.windwheel.common.utils.AuthContextHolder;
import cn.windwheel.common.utils.R;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 缺料预警：欠料清单、预警台账、规则覆盖、推送记录与出站拉取。
 * <p>
 * 条目键是业务自然键，可能含冒号等保留字符。路径形式的接口保持方案契约；
 * 键里含斜杠等无法进路径的字符时，用 /items/detail 与 /items/handle 两个等价入口。
 */
@Api(tags = "[生产计划]缺料预警")
@RestController
@RequestMapping("/shortage")
@RequiredArgsConstructor
@RequirePermission("aps:shortage:view")
public class ShortageController {
    static final int MAX_PAGE = 500;

    private final ShortageItemStore items;
    private final ShortageAlertStore alerts;
    private final ShortageNotifyStore notifies;
    private final ShortageRuleStore rules;
    private final ShortageAlertService alertService;
    private final ShortageScanService scan;
    private final ShortageViews views;

    @ModelAttribute
    public void authenticated(HttpServletRequest request) {
        String source = (String) request.getAttribute(AuthSessionKeys.REQUEST_AUTH_CONTEXT_SOURCE);
        if (!"session".equals(source) && !"service".equals(source)) {
            throw new ShortageException(403, "需要已认证的登录会话或服务账号");
        }
    }

    public record HandleRequest(String shortageKey, String action, String note) {
    }

    @ApiOperation("欠料清单查询，默认只看未关闭的条目")
    @GetMapping("/items")
    public R<?> items(@RequestParam(required = false) String dimension,
                      @RequestParam(required = false) String severity,
                      @RequestParam(required = false) String status,
                      @RequestParam(required = false) String orderCode,
                      @RequestParam(required = false) String materialCode,
                      @RequestParam(defaultValue = "false") boolean includeClosed,
                      @RequestParam(defaultValue = "0") int offset,
                      @RequestParam(defaultValue = "50") int limit) {
        page(offset, limit);
        String tenant = tenant();
        Dimension dim = dimension(dimension);
        Severity level = severity(severity);
        ItemState state = state(status, ItemState.class);
        Map<String, Object> body = views.page(
                items.count(tenant, dim, level, state, includeClosed, orderCode, materialCode), offset, limit,
                views.items(items.list(tenant, dim, level, state, includeClosed, orderCode, materialCode, offset, limit)));
        // 汇总跟查询条件无关，始终给出两条线各自的条数，界面据此按线分组并折叠水位线。
        body.put("summary", views.summary(items.summary(tenant)));
        return R.ok(body);
    }

    @ApiOperation("单条欠料明细，含在途明细")
    @GetMapping({"/items/detail", "/items/{shortageKey}"})
    public R<?> item(@RequestParam(required = false) String shortageKey,
                     @PathVariable(name = "shortageKey", required = false) String pathKey) {
        String key = shortageKey != null ? shortageKey : pathKey;
        if (key == null || key.isBlank()) {
            throw new ShortageException(422, "缺少 shortageKey");
        }
        ShortageItem item = items.find(tenant(), key).orElseThrow(() -> ShortageException.missing("欠料条目"));
        return R.ok(views.item(item));
    }

    @ApiOperation("条目级回执：ack 确认、resolve 解决、mute 静默（P1 拒绝）")
    @PostMapping({"/items/handle", "/items/{shortageKey}/ack", "/items/{shortageKey}/resolve",
            "/items/{shortageKey}/mute"})
    @RequirePermission("aps:shortage:handle")
    @AuditOperation("处理缺料条目")
    public R<?> handleItem(@PathVariable(name = "shortageKey", required = false) String pathKey,
                           HttpServletRequest request, @RequestBody(required = false) HandleRequest body) {
        String key = pathKey != null ? pathKey : body == null ? null : body.shortageKey();
        if (key == null || key.isBlank()) {
            throw new ShortageException(422, "缺少 shortageKey");
        }
        Receipt action = pathKey == null ? receipt(body.action()) : receipt(lastSegment(request));
        alertService.handleItem(tenant(), key, action, actor(), body == null ? null : body.note(), LocalDateTime.now());
        return R.ok();
    }

    @ApiOperation("预警台账")
    @GetMapping("/alerts")
    public R<?> alerts(@RequestParam(required = false) String dimension,
                       @RequestParam(required = false) String severity,
                       @RequestParam(required = false) String status,
                       @RequestParam(required = false) String ruleCode,
                       @RequestParam(required = false) String since,
                       @RequestParam(defaultValue = "0") int offset,
                       @RequestParam(defaultValue = "50") int limit) {
        page(offset, limit);
        ShortageRule rule = rule(ruleCode);
        LocalDateTime from = null;
        if (since != null && !since.isBlank()) {
            try {
                from = LocalDateTime.parse(since);
            } catch (Exception e) {
                throw new ShortageException(422, "since 须为 ISO 本地时间，例如 2026-11-27T00:00:00");
            }
        }
        ShortageAlertStore.Page result = alerts.list(tenant(), dimension(dimension), severity(severity),
                state(status, AlertState.class), rule, from, offset, limit);
        return R.ok(views.page(result.total(), offset, limit, views.alerts(result.records())));
    }

    @ApiOperation("单条预警")
    @GetMapping("/alerts/{alertId}")
    public R<?> alert(@PathVariable String alertId) {
        ShortageAlert alert = alerts.find(tenant(), alertId).orElseThrow(() -> ShortageException.missing("预警"));
        return R.ok(views.alert(alert));
    }

    @ApiOperation("预警级回执，供渠道回调与外部系统使用；无会话时按签名认证")
    @PostMapping({"/alerts/{alertId}/ack", "/alerts/{alertId}/resolve", "/alerts/{alertId}/mute"})
    @RequirePermission("aps:shortage:dispatch")
    @AuditOperation("预警回执")
    public R<?> alertReceipt(@PathVariable String alertId, HttpServletRequest request,
                             @RequestBody(required = false) HandleRequest body) {
        alertService.receipt(tenant(), alertId, receipt(lastSegment(request)), actor(),
                body == null ? null : body.note(), LocalDateTime.now());
        return R.ok();
    }

    @ApiOperation("生效规则：默认值叠加租户覆盖")
    @GetMapping("/rules")
    public R<?> rules() {
        Map<ShortageRule, ShortageRuleStore.Change> overridden = new LinkedHashMap<>();
        rules.overrides(tenant()).forEach((rule, override) -> overridden.put(rule, new ShortageRuleStore.Change(
                override.severity(), override.enabled(), override.thresholdValue(), override.thresholdUnit(),
                override.cooldownMinutes(), override.receivers(), override.channels(), override.actionHint())));
        List<Map<String, Object>> rows = new ArrayList<>();
        rules.effective(tenant()).forEach(e -> rows.add(views.rule(e, overridden.get(e.rule()))));
        return R.ok(rows);
    }

    @ApiOperation("规则覆盖，只传要改的字段")
    @PutMapping("/rules/{ruleCode}")
    @RequirePermission("aps:shortage:config")
    @AuditOperation("覆盖缺料预警规则")
    public R<?> saveRule(@PathVariable String ruleCode, @RequestBody ShortageRuleStore.Change change) {
        if (change == null) {
            throw new ShortageException(422, "缺少规则覆盖内容");
        }
        ShortageRule rule = rule(ruleCode);
        if (rule == null) {
            throw ShortageException.missing("规则");
        }
        rules.save(tenant(), rule, change, actor(), LocalDateTime.now());
        return R.ok(views.rule(alertService.effective(tenant(), rule),
                rules.current(tenant(), rule).orElse(null)));
    }

    @ApiOperation("推送记录与回执")
    @GetMapping("/notifies")
    public R<?> notifies(@RequestParam(required = false) String alertId,
                         @RequestParam(required = false) String channel,
                         @RequestParam(required = false) String status,
                         @RequestParam(defaultValue = "0") int offset,
                         @RequestParam(defaultValue = "50") int limit) {
        page(offset, limit);
        List<ShortageNotify> rows;
        long total;
        if (alertId != null && !alertId.isBlank()) {
            List<ShortageNotify> all = notifies.byAlert(tenant(), alertId);
            total = all.size();
            rows = all.subList(Math.min(offset, all.size()), Math.min(offset + limit, all.size()));
        } else if (channel != null && !channel.isBlank()) {
            NotifyState state = NotifyState.PENDING;
            if (status != null && !status.isBlank()) {
                state = state(status, NotifyState.class);
            }
            if (Channel.OUTBOX.name().equalsIgnoreCase(channel.trim())) {
                ShortageNotifyStore.Page page = notifies.outbox(tenant(), state, offset, limit);
                return R.ok(views.page(page.total(), offset, limit, views.notifies(page.records(), true)));
            }
            throw new ShortageException(422, "按渠道查询目前只支持 OUTBOX");
        } else {
            throw new ShortageException(422, "请指定 alertId 或 channel=OUTBOX");
        }
        return R.ok(views.page(total, offset, limit, views.notifies(rows, true)));
    }

    @ApiOperation("手工触发扫描：复评欠料条目与数据质量规则，并立刻投递一轮")
    @PostMapping("/scan")
    @RequirePermission("aps:shortage:config")
    @AuditOperation("手工触发缺料扫描")
    public R<?> scan() {
        ShortageScanService.Report report = scan.scan(tenant(), LocalDateTime.now());
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("tenantId", report.tenant());
        body.put("at", report.at().toString());
        body.put("itemsEvaluated", report.itemsSeen());
        body.put("alerts", report.alerts());
        body.put("dataQuality", report.dataQuality().notes());
        body.put("dispatch", report.dispatch());
        return R.ok(body);
    }

    @ApiOperation("拉取出站：给 ERP、SRM 的结构化缺料预警。拉取不改状态，回执才改")
    @GetMapping("/outbox")
    @RequirePermission("aps:shortage:dispatch")
    public R<?> outbox(@RequestParam(defaultValue = "PENDING") String state,
                       @RequestParam(defaultValue = "0") int offset,
                       @RequestParam(defaultValue = "100") int limit) {
        page(offset, limit);
        ShortageNotifyStore.Page page = notifies.outbox(tenant(), state(state, NotifyState.class), offset, limit);
        return R.ok(views.page(page.total(), offset, limit, views.notifies(page.records(), true)));
    }

    public record DeliveryRequest(String note) {
    }

    @ApiOperation("出站回执：外部系统确认收到，以返回的 id 做幂等键")
    @PostMapping("/outbox/{id}/ack")
    @RequirePermission("aps:shortage:dispatch")
    @AuditOperation("出站回执确认")
    public R<?> outboxAck(@PathVariable String id, @RequestBody(required = false) DeliveryRequest body) {
        outboxRow(id);
        notifies.deliveryReceipt(tenant(), id, true, body == null ? null : body.note(), actor(), LocalDateTime.now());
        alerts.markPushed(tenant(), notifies.find(tenant(), id).orElseThrow().getAlertId(), LocalDateTime.now());
        return R.ok();
    }

    @ApiOperation("出站回执：外部系统拒收")
    @PostMapping("/outbox/{id}/reject")
    @RequirePermission("aps:shortage:dispatch")
    @AuditOperation("出站回执拒收")
    public R<?> outboxReject(@PathVariable String id, @RequestBody DeliveryRequest body) {
        outboxRow(id);
        if (body == null || body.note() == null || body.note().isBlank()) {
            throw new ShortageException(422, "拒收必须填写理由");
        }
        notifies.deliveryReceipt(tenant(), id, false, body.note(), actor(), LocalDateTime.now());
        return R.ok();
    }

    private void outboxRow(String id) {
        ShortageNotify row = notifies.find(tenant(), id).orElseThrow(() -> ShortageException.missing("出站条目"));
        if (row.getChannel() != Channel.OUTBOX) {
            throw ShortageException.missing("出站条目");
        }
    }

    @ExceptionHandler(ShortageException.class)
    public ResponseEntity<?> shortage(ShortageException e) {
        return ResponseEntity.status(e.getCode()).body(R.failed(e.getCode(), e.getMessage()));
    }

    private static void page(int offset, int limit) {
        if (offset < 0 || limit < 1 || limit > MAX_PAGE) {
            throw new ShortageException(422, "分页范围无效");
        }
    }

    private static String lastSegment(HttpServletRequest request) {
        String uri = request.getRequestURI();
        return uri.substring(uri.lastIndexOf('/') + 1);
    }

    /** 契约路径用动词（ack/resolve/mute），台账状态用分词（ACKNOWLEDGED/RESOLVED/MUTED），两种写法都认。 */
    private static Receipt receipt(String action) {
        if (action == null || action.isBlank()) {
            throw new ShortageException(422, "缺少回执动作");
        }
        return switch (action.trim().toUpperCase()) {
            case "ACK", "ACKNOWLEDGE", "ACKNOWLEDGED" -> Receipt.ACK;
            case "RESOLVE", "RESOLVED" -> Receipt.RESOLVED;
            case "MUTE", "MUTED" -> Receipt.MUTED;
            default -> throw new ShortageException(422, "回执动作只支持 ACK、RESOLVED、MUTED");
        };
    }

    private static Dimension dimension(String value) {
        return value == null || value.isBlank() ? null : enumValue(value, Dimension.class);
    }

    private static Severity severity(String value) {
        return value == null || value.isBlank() ? null : enumValue(value, Severity.class);
    }

    private static ShortageRule rule(String code) {
        return code == null || code.isBlank() ? null : ShortageRule.parse(code.trim());
    }

    private static <T extends Enum<T>> T state(String value, Class<T> type) {
        return value == null || value.isBlank() ? null : enumValue(value, type);
    }

    private static <T extends Enum<T>> T enumValue(String value, Class<T> type) {
        try {
            return Enum.valueOf(type, value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new ShortageException(422, "未知取值：" + value);
        }
    }

    private static String tenant() {
        String tenant = AuthContextHolder.getTenantId();
        if (tenant == null || tenant.isBlank()) {
            throw new ShortageException(401, "缺少租户上下文");
        }
        return tenant;
    }

    private static String actor() {
        String user = AuthContextHolder.getUserId();
        return user == null || user.isBlank() ? "unknown" : user;
    }
}
