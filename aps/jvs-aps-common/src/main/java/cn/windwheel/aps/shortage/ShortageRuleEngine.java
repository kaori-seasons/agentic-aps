package cn.windwheel.aps.shortage;

import cn.windwheel.aps.shortage.ShortageModel.Dimension;
import cn.windwheel.aps.shortage.ShortageModel.Severity;
import cn.windwheel.aps.shortage.ShortageModel.SupplyMode;
import cn.windwheel.aps.shortage.ShortageRule.Effective;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * 规则引擎：一条欠料记录加上生效规则与当前时刻，得到命中的规则、标题与最高等级。
 * <p>
 * 纯计算，不碰库。等级只由规则决定，不由数据自己决定；同一份缺口在不同规则下可以是不同等级，取最高。
 * 时间是输入之一：同一份缺口今天只值 P3，三天后自然升到 P2，所以调用方必须周期性复评。
 */
@Component
public class ShortageRuleEngine {

    public record Match(ShortageRule rule, Severity severity, String title) {}

    public record Evaluation(List<Match> matches, Severity severity, String reasonCode) {
        static final Evaluation NONE = new Evaluation(List.of(), null, null);
    }

    /** 三条窗口规则由紧到松，命中第一条就停，一份缺口在同一族里只保留最高等级。 */
    private static final List<ShortageRule> GAP_WINDOWS = List.of(ShortageRule.MRP_GAP_FROZEN,
            ShortageRule.MRP_GAP_3D, ShortageRule.MRP_GAP_14D);

    private static final Map<String, ShortageRule> DEMAND_REASONS = Map.of(
            "SHORTAGE", ShortageRule.DEMAND_SHORTAGE,
            "BELOW_SAFETY", ShortageRule.DEMAND_BELOW_SAFETY,
            "STALE_ETA", ShortageRule.DEMAND_STALE_ETA,
            "ORDER_LIMIT", ShortageRule.DEMAND_PROPOSAL_BLOCKED,
            "STORAGE_CAPACITY", ShortageRule.DEMAND_PROPOSAL_BLOCKED,
            "PURCHASE_BUDGET", ShortageRule.DEMAND_PROPOSAL_BLOCKED,
            "PURCHASE_PENDING_RECONCILIATION", ShortageRule.DEMAND_PENDING_RECONCILE);

    /** 需求侧 Finding.reason 到规则码的映射，INACTIVE_REVIEW 与缺料无关，不映射。 */
    public static ShortageRule demandRule(String reason) {
        return DEMAND_REASONS.get(reason);
    }

    public Evaluation evaluate(ShortageItem item, Duration leadTime, Duration bufferTime,
                               Function<ShortageRule, Effective> rules, LocalDateTime now) {
        if (item.getDimension() == Dimension.ORDER) {
            return orderLine(item, leadTime, bufferTime, rules, now);
        }
        if (item.getDimension() == Dimension.WEEK) {
            return weekLine(item, rules, now);
        }
        return Evaluation.NONE;
    }

    private Evaluation orderLine(ShortageItem item, Duration leadTime, Duration bufferTime,
                                 Function<ShortageRule, Effective> rules, LocalDateTime now) {
        List<Match> matches = new ArrayList<>();
        boolean gap = item.getGapQuantity() != null && item.getGapQuantity().signum() > 0;
        if (gap) {
            for (ShortageRule window : GAP_WINDOWS) {
                Effective rule = rules.apply(window);
                if (!rule.enabled() || rule.thresholdDays() == null || item.getRequiredAt() == null) {
                    continue;
                }
                // requiredAt 不晚于 now + 阈值天即命中，恰好在边界上算命中。
                if (!item.getRequiredAt().isAfter(now.plusDays(rule.thresholdDays()))) {
                    matches.add(new Match(window, rule.severity(), gapTitle(window, item)));
                    break;
                }
            }
        }
        boolean hasLines = positive(item.getInTransitConsumed()) || positive(item.getInTransitSkipped());
        // 有在途却全部晚于交期：这批货根本赶不上，订单已确定无法按期。
        if (hasLines && !positive(item.getInTransitConsumed())) {
            add(matches, ShortageRule.MRP_LEADTIME_VIOLATION, rules,
                    "订单 " + nvl(item.getOrderCode()) + " 物料 " + nvl(item.getMaterialCode()) + " 的在途全部晚于交期 "
                            + ShortageText.local(item.getRequiredAt()) + "，最早 " + ShortageText.local(item.getExpectedEta())
                            + " 可用，缺 " + ShortageText.quantity(item.getGapQuantity()) + " " + nvl(item.getUnit()));
        } else if (positive(item.getInTransitSkipped())) {
            add(matches, ShortageRule.MRP_ETA_PASSED, rules,
                    "订单 " + nvl(item.getOrderCode()) + " 物料 " + nvl(item.getMaterialCode()) + " 有 "
                            + ShortageText.quantity(item.getInTransitSkipped()) + " " + nvl(item.getUnit())
                            + " 在途到货晚于交期 " + ShortageText.local(item.getRequiredAt()));
        }
        // 无在途且无采购建议。方案把它定为 P1，这里收紧为“现在下单也来不及或已进冻结期”才升 P1：
        // 对每一处尚有余量的缺口都报 P1，会把真正上线受阻的单子淹没掉。
        if (gap && item.getSupplyMode() == SupplyMode.NONE && !hasLines && item.getRequiredAt() != null) {
            LocalDateTime earliest = now.plus(leadTime).plus(bufferTime);
            Effective frozen = rules.apply(ShortageRule.MRP_GAP_FROZEN);
            boolean unfixable = earliest.isAfter(item.getRequiredAt())
                    || frozen.enabled() && frozen.thresholdDays() != null
                    && !item.getRequiredAt().isAfter(now.plusDays(frozen.thresholdDays()));
            if (unfixable) {
                add(matches, ShortageRule.MRP_NO_SUPPLY, rules,
                        "订单 " + nvl(item.getOrderCode()) + " 物料 " + nvl(item.getMaterialCode())
                                + " 无在途无采购建议，缺 " + ShortageText.quantity(item.getGapQuantity()) + " "
                                + nvl(item.getUnit()) + "，现在下单也来不及");
            }
        }
        return conclude(matches, item.getReasonCode());
    }

    private Evaluation weekLine(ShortageItem item, Function<ShortageRule, Effective> rules, LocalDateTime now) {
        ShortageRule rule = demandRule(item.getSourceReason());
        if (rule == null) {
            return Evaluation.NONE;
        }
        List<Match> matches = new ArrayList<>();
        Effective effective = rules.apply(rule);
        if (!effective.enabled()) {
            return conclude(matches, item.getReasonCode());
        }
        Severity severity = effective.severity();
        // 待对账超时升级：默认 24 小时升 P2，从本轮连续观测的起点开始算。
        if (rule == ShortageRule.DEMAND_PENDING_RECONCILE && effective.thresholdMinutes() != null
                && item.getFirstDetectedAt() != null
                && !item.getFirstDetectedAt().plusMinutes(effective.thresholdMinutes()).isAfter(now)) {
            severity = severity.atLeast(Severity.P2) ? severity : Severity.P2;
        }
        matches.add(new Match(rule, severity, weekTitle(rule, item, now)));
        return conclude(matches, item.getReasonCode());
    }

    private static void add(List<Match> matches, ShortageRule rule, Function<ShortageRule, Effective> rules, String title) {
        Effective effective = rules.apply(rule);
        if (effective.enabled()) {
            matches.add(new Match(rule, effective.severity(), ShortageText.trim(title, 255)));
        }
    }

    private static Evaluation conclude(List<Match> matches, String fallbackReason) {
        Match top = Severity.highest(matches, Match::severity);
        if (top == null) {
            // 未命中任何规则时保留观测类别作为原因码，等级留空。
            return new Evaluation(List.copyOf(matches), null, fallbackReason);
        }
        return new Evaluation(List.copyOf(matches), top.severity(), top.rule().name());
    }

    private static String gapTitle(ShortageRule rule, ShortageItem item) {
        String head = "订单 " + nvl(item.getOrderCode()) + " 缺料 " + nvl(item.getMaterialCode());
        if (item.getMaterialName() != null && !item.getMaterialName().isBlank()) {
            head += "（" + item.getMaterialName() + "）";
        }
        return head + "，缺 " + ShortageText.quantity(item.getGapQuantity()) + " " + nvl(item.getUnit())
                + "，交期 " + ShortageText.local(item.getRequiredAt());
    }

    private static String weekTitle(ShortageRule rule, ShortageItem item, LocalDateTime now) {
        String scope = nvl(item.getLocationCode()) + " 物料 " + nvl(item.getMaterialCode());
        String qty = ShortageText.quantity(item.getGapQuantity()) + " " + nvl(item.getUnit());
        String week = item.getWeekStart() == null ? "" : "第 " + item.getWeekStart() + " 周";
        return switch (rule) {
            case DEMAND_SHORTAGE -> scope + " " + week + "预测水位为负，低于安全库存 " + qty;
            case DEMAND_BELOW_SAFETY -> scope + " " + week + "低于安全水位 " + qty;
            case DEMAND_STALE_ETA -> scope + " 在途 " + nvl(item.getReference()) + " 承诺到货 "
                    + ShortageText.local(item.getRequiredAt()) + " 已过期，未清余量 " + qty;
            case DEMAND_PROPOSAL_BLOCKED -> scope + " 采购建议被 " + nvl(item.getSourceReason())
                    + " 卡住，差额 " + qty;
            case DEMAND_PENDING_RECONCILE -> scope + " 采购单 " + nvl(item.getReference()) + " 待对账，自 "
                    + ShortageText.local(item.getFirstDetectedAt()) + " 起";
            default -> scope + " " + week + " " + rule.name();
        };
    }

    private static boolean positive(BigDecimal value) {
        return value != null && value.signum() > 0;
    }

    private static String nvl(String value) {
        return value == null ? "-" : value;
    }
}
