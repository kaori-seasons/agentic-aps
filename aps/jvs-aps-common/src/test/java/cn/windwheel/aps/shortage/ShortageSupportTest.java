package cn.windwheel.aps.shortage;

import cn.windwheel.aps.shortage.ShortageModel.AlertState;
import cn.windwheel.aps.shortage.ShortageModel.Channel;
import cn.windwheel.aps.shortage.ShortageModel.Severity;
import cn.windwheel.aps.shortage.ShortageModel.SupplyMode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;


/** 不碰库的支撑逻辑：退避、键、信封、对外口径。 */
class ShortageSupportTest {
    private static final LocalDateTime AT = LocalDateTime.of(2026, 11, 27, 8, 12, 33, 123_000_000);

    /** 失败退避重试三次：1 分钟、5 分钟、25 分钟，用尽后不再重试。 */
    @Test
    void retryBackoffHasThreeStepsThenGivesUp() {
        ShortageProperties props = new ShortageProperties();
        assertEquals(Duration.ofMinutes(1), props.backoff(1));
        assertEquals(Duration.ofMinutes(5), props.backoff(2));
        assertEquals(Duration.ofMinutes(25), props.backoff(3));
        assertNull(props.backoff(4), "第四次失败即进失败态");
        assertNull(props.backoff(0));
    }

    @Test
    void shortageKeyIsTheBusinessKeyWithoutRunId() {
        // 键里没有 runId：不同轮次命中同一条，按同键覆盖而不是每轮新增一行。
        assertEquals("ORDER:WO-2026-00417:M-10023", ShortageItemService.orderKey("WO-2026-00417", "M-10023"));
        assertEquals(ShortageItemService.orderKey("WO-1", "M-1"), ShortageItemService.orderKey("WO-1", "M-1"));
        String week = ShortageItemService.naturalKey("WEEK", "plant-01", "M-1", "DEMAND_SHORTAGE", "-", "2026-11-30");
        assertEquals("WEEK:plant-01:M-1:DEMAND_SHORTAGE:-:2026-11-30", week);
    }

    /** 订单编码最长 255、物料键最长 96，拼起来会超过列宽，超长时改用内容摘要收尾。 */
    @Test
    void overlongKeysFallBackToADigestSuffixWithoutLosingUniqueness() {
        String longOrder = "WO-" + "x".repeat(400);
        String key = ShortageItemService.orderKey(longOrder, "M-1");
        assertTrue(key.length() <= 400, "length=" + key.length());
        assertTrue(key.startsWith("ORDER:WO-xxxx"));
        assertTrue(key.contains("#"));
        assertEquals(key, ShortageItemService.orderKey(longOrder, "M-1"), "同样的输入必须得到同样的键");
        assertNotEquals(key, ShortageItemService.orderKey(longOrder + "y", "M-1"));
        assertNotEquals(key, ShortageItemService.orderKey(longOrder, "M-2"));
    }

    @Test
    void notifyIdempotencyKeySeparatesTargetsOnTheSameChannel() {
        String alertId = "a5b4c6d8-7f9a-42a4-8f35-0a1b2c3d4e6f";
        String wecom = ShortageNotifyStore.idempotencyKey(alertId, Channel.WEBHOOK, "https://qyapi.example.local/hook/a");
        String dingtalk = ShortageNotifyStore.idempotencyKey(alertId, Channel.WEBHOOK, "https://oapi.example.local/hook/b");
        assertNotEquals(wecom, dingtalk, "同一渠道配多个接收端时不能撞键");
        assertEquals(wecom, ShortageNotifyStore.idempotencyKey(alertId, Channel.WEBHOOK, "https://qyapi.example.local/hook/a"));
        assertNotEquals(wecom, ShortageNotifyStore.idempotencyKey(alertId, Channel.EMAIL, "https://qyapi.example.local/hook/a"));
        assertTrue(wecom.length() <= 120);
    }

    @Test
    void wireQuantitiesAreDecimalStringsAndTimesCarryAnOffset() {
        assertEquals("120.000000", ShortageText.decimal(new BigDecimal("120")));
        assertEquals("0.500000", ShortageText.decimal(new BigDecimal("0.5")));
        assertEquals("120", ShortageText.quantity(new BigDecimal("120.000000")));
        assertEquals("1000", ShortageText.quantity(new BigDecimal("1E+3")));
        assertEquals("2026-11-27T08:12:33+08:00", ShortageText.iso(AT, ZoneId.of("Asia/Shanghai")));
        assertEquals("2026-11-27T08:12:33Z", ShortageText.iso(AT, ZoneId.of("UTC")));
        assertNull(ShortageText.decimal(null));
        assertNull(ShortageText.iso((LocalDateTime) null, ZoneId.of("UTC")));
        assertEquals(Duration.ofDays(2), ShortageText.duration("P2D"));
        assertEquals(Duration.ZERO, ShortageText.duration("not-a-duration"));
        assertEquals(Duration.ZERO, ShortageText.duration("-P1D"), "负时长按零处理");
    }

    @Test
    void envelopeCarriesTheAlertContract() throws Exception {
        ShortageProperties props = new ShortageProperties();
        props.setSourceInstance("aps-prod-01");
        AlertEnvelope envelope = new AlertEnvelope(new ObjectMapper(), props);
        ShortageAlert alert = alert();

        // 没配置对外基地址就不下发 ackUrl。
        String json = envelope.build(alert, "e4c3d5f7-6a8b-4193-9e24-9f0a1b3c4d5e", Channel.WEBHOOK, envelope.ackUrl(alert.getAlertId()));
        Map<?, ?> tree = new ObjectMapper().convertValue(read(json), Map.class);
        assertEquals("1.0", tree.get("envelopeVersion"));
        assertEquals("ALERT", tree.get("messageType"));
        assertEquals("APS", tree.get("sourceSystem"));
        assertEquals("aps-prod-01", tree.get("sourceInstance"));
        assertEquals("plant-01", tree.get("tenantId"));
        assertEquals(alert.getAlertId(), tree.get("alertId"));
        assertEquals(alert.getAlertKey(), tree.get("alertKey"));
        Map<?, ?> payload = (Map<?, ?>) tree.get("payload");
        assertEquals("MRP_GAP_FROZEN", payload.get("ruleCode"));
        assertEquals("P1", payload.get("severity"));
        assertEquals("ORDER", payload.get("dimension"));
        assertEquals("120.000000", payload.get("gapQuantity"));
        assertEquals("2026-11-28T08:00:00+08:00", payload.get("requiredAt"));
        assertEquals("立即催料或改期，准备分批", payload.get("actionHint"));
        // 空值显式呈现为“无到货计划”，不能省略。
        assertEquals("NONE", payload.get("supplyMode"));
        assertEquals("无到货计划", payload.get("supplyPlan"));
        assertNull(payload.get("expectedEta"));
        assertNull(payload.get("ackUrl"));

        props.setPublicBaseUrl("https://aps.example.local/mgr/jvs-aps/");
        String withUrl = envelope.build(alert, "m2", Channel.WEBHOOK, envelope.ackUrl(alert.getAlertId()));
        Map<?, ?> withAck = (Map<?, ?>) new ObjectMapper().convertValue(read(withUrl), Map.class).get("payload");
        assertEquals("https://aps.example.local/mgr/jvs-aps/shortage/alerts/" + alert.getAlertId() + "/ack",
                withAck.get("ackUrl"));
    }

    /** 无阈值规则（如 MRP_NO_SUPPLY）序列化时不能因自动拆箱 NPE，阈值就输出 null。 */
    @Test
    void ruleViewsWithoutAThresholdSerializeFine() {
        ShortageViews views = new ShortageViews(new ObjectMapper(), new ShortageProperties());
        Map<String, Object> none = views.rule(ShortageRuleStore.merge(ShortageRule.MRP_NO_SUPPLY, null), null);
        assertNull(none.get("thresholdValue"));
        assertNull(none.get("thresholdUnit"));
        assertEquals("P1", none.get("severity"));
        Map<String, Object> days = views.rule(ShortageRuleStore.merge(ShortageRule.MRP_GAP_3D, null), null);
        assertEquals(3, days.get("thresholdValue"));
        assertEquals("DAYS", days.get("thresholdUnit"));
        Map<String, Object> minutes = views.rule(ShortageRuleStore.merge(ShortageRule.DATA_SNAPSHOT_EXPIRED, null), null);
        assertEquals(1440, minutes.get("thresholdValue"));
        assertEquals("MINUTES", minutes.get("thresholdUnit"));
    }

    @Test
    void emailIsOnlyForP1AndP2() {
        assertTrue(AlertEnvelope.mailEligible(Channel.EMAIL, Severity.P1));
        assertTrue(AlertEnvelope.mailEligible(Channel.EMAIL, Severity.P2));
        assertFalse(AlertEnvelope.mailEligible(Channel.EMAIL, Severity.P3));
        assertFalse(AlertEnvelope.mailEligible(Channel.EMAIL, Severity.P4));
        assertFalse(AlertEnvelope.mailEligible(Channel.WEBHOOK, Severity.P1));
    }

    @Test
    void onlyWebhookAndEmailArePushedByTheWorker() {
        // 出站通道等外部来拉，台账在创建时即已送达，二者都不进投递认领。
        String channels = ShortageNotifyStore.PUSH_CHANNELS;
        assertTrue(channels.contains("WEBHOOK") && channels.contains("EMAIL"));
        assertFalse(channels.contains("OUTBOX"));
        assertFalse(channels.contains("LEDGER"));
    }

    @Test
    void defaultZoneFallsBackToTheJvmZone() {
        ShortageProperties props = new ShortageProperties();
        assertEquals(ZoneId.systemDefault(), props.zone());
        props.setZoneId("Asia/Tokyo");
        assertEquals(ZoneId.of("Asia/Tokyo"), props.zone());
    }

    private static ShortageAlert alert() {
        ShortageAlert alert = new ShortageAlert()
                .setTenantId("plant-01")
                .setAlertId("a5b4c6d8-7f9a-42a4-8f35-0a1b2c3d4e6f")
                .setAlertKey("MRP_GAP_FROZEN:ORDER:WO-2026-00417:M-10023:20261127T08")
                .setRule(ShortageRule.MRP_GAP_FROZEN)
                .setSeverity(Severity.P1)
                .setState(AlertState.OPEN)
                .setFirstObservedAt(AT)
                .setLastObservedAt(AT)
                .setCreatedAt(AT)
                .setPayload("{\"ruleCode\":\"MRP_GAP_FROZEN\",\"severity\":\"P1\",\"dimension\":\"ORDER\","
                        + "\"title\":\"订单 WO-2026-00417 缺料 M-10023，120 件\","
                        + "\"gapQuantity\":\"120.000000\",\"requiredAt\":\"2026-11-28T08:00:00+08:00\","
                        + "\"actionHint\":\"立即催料或改期，准备分批\",\"supplyMode\":\"NONE\","
                        + "\"supplyPlan\":\"无到货计划\",\"expectedEta\":null}");
        return alert;
    }

    private static Object read(String json) throws Exception {
        return new ObjectMapper().readValue(json, Object.class);
    }
}
