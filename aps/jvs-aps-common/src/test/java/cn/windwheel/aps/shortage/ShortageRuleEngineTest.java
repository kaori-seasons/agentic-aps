package cn.windwheel.aps.shortage;

import cn.windwheel.aps.shortage.ShortageModel.Channel;
import cn.windwheel.aps.shortage.ShortageModel.Dimension;
import cn.windwheel.aps.shortage.ShortageModel.Severity;
import cn.windwheel.aps.shortage.ShortageModel.Source;
import cn.windwheel.aps.shortage.ShortageModel.SupplyMode;
import cn.windwheel.aps.shortage.ShortageRule.Effective;
import cn.windwheel.aps.shortage.ShortageRuleEngine.Evaluation;
import cn.windwheel.aps.shortage.ShortageRuleEngine.Match;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 规则引擎是纯计算，等级只由规则、数据与当前时刻决定。
 */
class ShortageRuleEngineTest {
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 27, 8, 0);
    private static final Duration LEAD = Duration.ofDays(2);
    private static final Duration NO_LEAD = Duration.ZERO;

    private final ShortageRuleEngine engine = new ShortageRuleEngine();

    private static Map<ShortageRule, Effective> defaults() {
        Map<ShortageRule, Effective> rules = new EnumMap<>(ShortageRule.class);
        for (ShortageRule rule : ShortageRule.values()) {
            rules.put(rule, ShortageRuleStore.merge(rule, null));
        }
        return rules;
    }

    private static Map<ShortageRule, Effective> with(ShortageRule rule, ShortageRuleStore.Override override) {
        Map<ShortageRule, Effective> rules = defaults();
        rules.put(rule, ShortageRuleStore.merge(rule, override));
        return rules;
    }

    private static ShortageRuleStore.Override override(Severity severity, Boolean enabled, Integer value, String unit) {
        return new ShortageRuleStore.Override(null, severity, enabled, value, unit, null, null, null, null, null, null);
    }

    private static ShortageItem mrp(String gap, LocalDateTime requiredAt, SupplyMode mode, String consumed, String skipped) {
        return new ShortageItem()
                .setShortageKey("ORDER:WO-1:RM-1").setDimension(Dimension.ORDER).setSource(Source.MRP)
                .setOrderCode("WO-1").setMaterialCode("RM-1").setMaterialName("高压胶管总成").setUnit("件")
                .setGapQuantity(new BigDecimal(gap)).setRequiredQuantity(new BigDecimal("120"))
                .setRequiredAt(requiredAt).setSupplyMode(mode)
                .setInTransitConsumed(new BigDecimal(consumed)).setInTransitSkipped(new BigDecimal(skipped))
                .setReasonCode("MRP_GAP").setFirstDetectedAt(NOW).setExpectedEta(requiredAt);
    }

    /** 有供给在途（哪怕只用上一部分）时，无供给规则不参与，窗口规则单独看。 */
    private static ShortageItem gapDueIn(LocalDateTime requiredAt) {
        return mrp("120", requiredAt, SupplyMode.IN_TRANSIT, "0", "0");
    }

    private static List<ShortageRule> rules(Evaluation evaluation) {
        return evaluation.matches().stream().map(Match::rule).toList();
    }

    private static List<ShortageRule> windowRules(Evaluation evaluation) {
        return evaluation.matches().stream().map(Match::rule)
                .filter(r -> r == ShortageRule.MRP_GAP_FROZEN || r == ShortageRule.MRP_GAP_3D
                        || r == ShortageRule.MRP_GAP_14D).toList();
    }

    /** 二期验收：同时落在冻结与三日窗口里时，只保留最高等级。 */
    @Test
    void frozenGapKeepsOnlyTheHighestWindowRule() {
        Evaluation evaluation = engine.evaluate(gapDueIn(NOW.plusHours(12)), LEAD, Duration.ZERO, defaults()::get, NOW);
        assertEquals(List.of(ShortageRule.MRP_GAP_FROZEN), windowRules(evaluation));
        assertEquals(Severity.P1, evaluation.severity());
        assertEquals("MRP_GAP_FROZEN", evaluation.reasonCode());
    }

    @Test
    void windowRulesStepDownWithTheRemainingTime() {
        Evaluation three = engine.evaluate(gapDueIn(NOW.plusDays(2)), LEAD, Duration.ZERO, defaults()::get, NOW);
        assertEquals(List.of(ShortageRule.MRP_GAP_3D), windowRules(three));
        assertEquals(Severity.P2, three.severity());

        Evaluation fourteen = engine.evaluate(gapDueIn(NOW.plusDays(10)), LEAD, Duration.ZERO, defaults()::get, NOW);
        assertEquals(List.of(ShortageRule.MRP_GAP_14D), windowRules(fourteen));
        assertEquals(Severity.P3, fourteen.severity());

        // 边界值算命中：恰好在第三天到期仍是三日窗口。
        Evaluation edge = engine.evaluate(gapDueIn(NOW.plusDays(3)), LEAD, Duration.ZERO, defaults()::get, NOW);
        assertEquals(List.of(ShortageRule.MRP_GAP_3D), windowRules(edge));
    }

    /** 未命中任何规则时没有等级，不是 P4：P4 是数据质量提示，不是“更轻的缺料”。 */
    @Test
    void gapOutsideEveryWindowHasNoSeverity() {
        Evaluation evaluation = engine.evaluate(gapDueIn(NOW.plusDays(20)), LEAD, Duration.ZERO, defaults()::get, NOW);
        assertTrue(evaluation.matches().isEmpty());
        assertNull(evaluation.severity());
        assertEquals("MRP_GAP", evaluation.reasonCode());
    }

    @Test
    void zeroGapMatchesNothing() {
        Evaluation evaluation = engine.evaluate(gapDueIn(NOW.plusHours(1)).setGapQuantity(BigDecimal.ZERO), LEAD,
                Duration.ZERO, defaults()::get, NOW);
        assertTrue(evaluation.matches().isEmpty());
    }

    @Test
    void lateInTransitRaisesEtaPassed() {
        // 按时到一部分、另一部分晚于交期：有货但到得太晚。
        Evaluation evaluation = engine.evaluate(mrp("8", NOW.plusDays(20), SupplyMode.IN_TRANSIT, "2", "20"), LEAD,
                Duration.ZERO, defaults()::get, NOW);
        assertTrue(rules(evaluation).contains(ShortageRule.MRP_ETA_PASSED));
        assertFalse(rules(evaluation).contains(ShortageRule.MRP_LEADTIME_VIOLATION));
        assertEquals(Severity.P2, evaluation.severity());
        String title = evaluation.matches().stream().filter(m -> m.rule() == ShortageRule.MRP_ETA_PASSED)
                .findFirst().orElseThrow().title();
        assertTrue(title.contains("WO-1") && title.contains("RM-1") && title.contains("20 件"), title);
    }

    @Test
    void inTransitThatIsEntirelyLateIsALeadTimeViolation() {
        LocalDateTime due = NOW.plusDays(10);
        ShortageItem item = mrp("100", due, SupplyMode.IN_TRANSIT_LATE, "0", "100").setExpectedEta(due.plusDays(2));
        Evaluation evaluation = engine.evaluate(item, LEAD, Duration.ZERO, defaults()::get, NOW);
        assertTrue(rules(evaluation).contains(ShortageRule.MRP_LEADTIME_VIOLATION));
        assertEquals(Severity.P1, evaluation.severity());
        assertEquals("MRP_LEADTIME_VIOLATION", evaluation.reasonCode());
    }

    /** 方案把“无在途且无采购建议”定为 P1。这里收紧为“现在下单也来不及或已进冻结期”才升 P1。 */
    @Test
    void noSupplyIsP1OnlyWhenOrderingNowIsAlreadyTooLate() {
        LocalDateTime soon = NOW.plusDays(5);
        // 还有时间下单：只按窗口报 P3，不升 P1。
        Evaluation fixable = engine.evaluate(mrp("100", soon, SupplyMode.NONE, "0", "0"), Duration.ofDays(1),
                Duration.ZERO, defaults()::get, NOW);
        assertFalse(rules(fixable).contains(ShortageRule.MRP_NO_SUPPLY));
        assertEquals(Severity.P3, fixable.severity());

        // 提前期十天，五天后就要用：现在下单也来不及。
        Evaluation tooLate = engine.evaluate(mrp("100", soon, SupplyMode.NONE, "0", "0"), Duration.ofDays(10),
                Duration.ZERO, defaults()::get, NOW);
        assertTrue(rules(tooLate).contains(ShortageRule.MRP_NO_SUPPLY));
        assertEquals(Severity.P1, tooLate.severity());

        // 已进冻结期，等不起。
        Evaluation frozen = engine.evaluate(mrp("100", NOW.plusHours(12), SupplyMode.NONE, "0", "0"), NO_LEAD,
                Duration.ZERO, defaults()::get, NOW);
        assertTrue(rules(frozen).contains(ShortageRule.MRP_NO_SUPPLY));

        // 缓冲期计入到货时间。
        Evaluation buffer = engine.evaluate(mrp("100", soon, SupplyMode.NONE, "0", "0"), Duration.ofDays(4),
                Duration.ofDays(2), defaults()::get, NOW);
        assertTrue(rules(buffer).contains(ShortageRule.MRP_NO_SUPPLY));

        // 没有需求时刻就无从判断来不来得及。
        Evaluation undated = engine.evaluate(mrp("100", null, SupplyMode.NONE, "0", "0"), Duration.ofDays(10),
                Duration.ZERO, defaults()::get, NOW);
        assertFalse(rules(undated).contains(ShortageRule.MRP_NO_SUPPLY));
    }

    @Test
    void disabledRuleYieldsToTheNextWindow() {
        Map<ShortageRule, Effective> rules = with(ShortageRule.MRP_GAP_FROZEN, override(null, false, null, null));
        Evaluation evaluation = engine.evaluate(gapDueIn(NOW.plusHours(12)), LEAD, Duration.ZERO, rules::get, NOW);
        assertEquals(List.of(ShortageRule.MRP_GAP_3D), windowRules(evaluation));
        assertEquals(Severity.P2, evaluation.severity());
    }

    @Test
    void tenantThresholdOverrideIsHonoured() {
        Map<ShortageRule, Effective> rules = with(ShortageRule.MRP_GAP_FROZEN, override(null, null, 3, "DAYS"));
        Evaluation evaluation = engine.evaluate(gapDueIn(NOW.plusDays(2)), LEAD, Duration.ZERO, rules::get, NOW);
        assertEquals(List.of(ShortageRule.MRP_GAP_FROZEN), windowRules(evaluation));
    }

    @Test
    void severityOverrideRaisesTheAlert() {
        Map<ShortageRule, Effective> rules = with(ShortageRule.MRP_GAP_14D, override(Severity.P1, null, null, null));
        Evaluation evaluation = engine.evaluate(gapDueIn(NOW.plusDays(10)), LEAD, Duration.ZERO, rules::get, NOW);
        assertEquals(Severity.P1, evaluation.severity());
    }

    private static ShortageItem demand(String reason, LocalDateTime firstDetected) {
        return new ShortageItem()
                .setShortageKey("WEEK:plant:RM-1:" + ShortageRuleEngine.demandRule(reason) + ":-:2026-11-30")
                .setDimension(Dimension.WEEK).setSource(Source.DEMAND).setSourceReason(reason)
                .setMaterialCode("RM-1").setUnit("件").setLocationCode("plant").setReference("-")
                .setGapQuantity(new BigDecimal("12")).setReasonCode(reason).setFirstDetectedAt(firstDetected);
    }

    @Test
    void demandFindingsMapToTheirRules() {
        assertEquals(ShortageRule.DEMAND_SHORTAGE, ShortageRuleEngine.demandRule("SHORTAGE"));
        assertEquals(ShortageRule.DEMAND_BELOW_SAFETY, ShortageRuleEngine.demandRule("BELOW_SAFETY"));
        assertEquals(ShortageRule.DEMAND_STALE_ETA, ShortageRuleEngine.demandRule("STALE_ETA"));
        for (String blocked : List.of("ORDER_LIMIT", "STORAGE_CAPACITY", "PURCHASE_BUDGET")) {
            assertEquals(ShortageRule.DEMAND_PROPOSAL_BLOCKED, ShortageRuleEngine.demandRule(blocked));
        }
        // INACTIVE_REVIEW 与缺料无关，不映射。
        assertNull(ShortageRuleEngine.demandRule("INACTIVE_REVIEW"));

        Evaluation shortage = engine.evaluate(demand("SHORTAGE", NOW), NO_LEAD, Duration.ZERO, defaults()::get, NOW);
        assertEquals(Severity.P2, shortage.severity());
        assertEquals("DEMAND_SHORTAGE", shortage.reasonCode());
        Evaluation below = engine.evaluate(demand("BELOW_SAFETY", NOW), NO_LEAD, Duration.ZERO, defaults()::get, NOW);
        assertEquals(Severity.P3, below.severity());
    }

    @Test
    void pendingReconciliationEscalatesAfterTheConfiguredAge() {
        Evaluation fresh = engine.evaluate(demand("PURCHASE_PENDING_RECONCILIATION", NOW.minusHours(1)), NO_LEAD,
                Duration.ZERO, defaults()::get, NOW);
        assertEquals(Severity.P3, fresh.severity());

        Evaluation stale = engine.evaluate(demand("PURCHASE_PENDING_RECONCILIATION", NOW.minusHours(25)), NO_LEAD,
                Duration.ZERO, defaults()::get, NOW);
        assertEquals(Severity.P2, stale.severity());
    }

    @Test
    void bucketsFollowTheSeverity() {
        assertEquals("20261127T08", Severity.P1.bucket(NOW));
        assertEquals("20261127T08", Severity.P2.bucket(NOW));
        assertEquals("20261127", Severity.P3.bucket(NOW));
        assertEquals("20261127", Severity.P4.bucket(NOW));
    }

    @Test
    void effectiveRulesAlwaysKeepTheLedgerAndConvertThresholds() {
        Effective plain = ShortageRuleStore.merge(ShortageRule.MRP_GAP_3D, null);
        assertEquals(3L, plain.thresholdDays());
        assertEquals(4320L, plain.thresholdMinutes());
        assertEquals(60, plain.cooldownMinutes());
        assertTrue(plain.channels().contains(Channel.EMAIL.name()));

        // 站内台账是唯一必选通道，租户不能把它覆盖掉。
        Effective stripped = ShortageRuleStore.merge(ShortageRule.MRP_NO_SUPPLY,
                new ShortageRuleStore.Override(null, Severity.P2, null, null, null, 240, "APS_SCHEDULER",
                        "WEBHOOK", "加急", "u", NOW));
        assertEquals(List.of("LEDGER", "WEBHOOK"), stripped.channels());
        assertEquals(Severity.P2, stripped.severity());
        assertEquals(240, stripped.cooldownMinutes());
        assertEquals(List.of("APS_SCHEDULER"), stripped.receivers());
        assertEquals("加急", stripped.actionHint());
        // 冷却期从预警创建时刻起算。
        assertTrue(stripped.allows(NOW.minusHours(4), NOW));
        assertFalse(stripped.allows(NOW.minusHours(3), NOW));

        Effective hours = ShortageRuleStore.merge(ShortageRule.DATA_SNAPSHOT_EXPIRED,
                new ShortageRuleStore.Override(null, null, null, 6, "HOURS", null, null, null, null, null, null));
        assertEquals(360L, hours.thresholdMinutes());
        assertNull(hours.thresholdDays());
    }

    @Test
    void severityOrdering() {
        assertTrue(Severity.P1.higherThan(Severity.P3));
        assertFalse(Severity.P3.higherThan(Severity.P3));
        assertTrue(Severity.P1.atLeast(Severity.P2));
        assertFalse(Severity.P3.atLeast(Severity.P2));
    }
}
