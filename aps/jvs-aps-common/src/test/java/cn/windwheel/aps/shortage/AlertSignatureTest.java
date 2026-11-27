package cn.windwheel.aps.shortage;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 出向签名按方案契约：X-Aps-Signature: sha256={HMAC(secret, body)}。
 * 入向回执签名是本次补上的，方案漏了它的认证设计。
 */
class AlertSignatureTest {
    private static final String SECRET = "local-only-secret";

    @Test
    void webhookSignatureMatchesTheContract() throws Exception {
        String body = "{\"alertId\":\"a5b4c6d8\"}";
        String signature = ShortageWebhookSender.sign(SECRET, body);
        assertTrue(signature.startsWith("sha256="), signature);
        // sha256= 加 64 位十六进制。
        assertEquals(71, signature.length());
        assertTrue(signature.substring(7).matches("[0-9a-f]{64}"), signature);
        // 算法就是标准的 HMAC-SHA256，接收端用任何语言都能复算。
        javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
        mac.init(new javax.crypto.spec.SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        assertEquals(java.util.HexFormat.of().formatHex(mac.doFinal(body.getBytes(StandardCharsets.UTF_8))),
                signature.substring(7));
        // 签名随正文变化，改一个字节就对不上。
        assertNotEquals(signature, ShortageWebhookSender.sign(SECRET, body + " "));
        assertThrows(Exception.class, () -> ShortageWebhookSender.sign("", body));
    }

    @Test
    void webhookSignatureVerificationRejectsTampering() throws Exception {
        String body = "{\"a\":1}";
        String signature = ShortageWebhookSender.sign(SECRET, body);
        assertTrue(ShortageWebhookSender.verify(SECRET, body, signature));
        assertFalse(ShortageWebhookSender.verify(SECRET, body + "1", signature), "正文被改");
        assertFalse(ShortageWebhookSender.verify("other-secret", body, signature), "密钥不同");
        assertFalse(ShortageWebhookSender.verify(SECRET, body, null), "缺少签名");
        assertFalse(ShortageWebhookSender.verify(SECRET, body, "sha1=" + signature.substring(7)), "前缀不对");
        assertFalse(ShortageWebhookSender.verify(SECRET, body, "zz"), "格式不对");
    }

    @Test
    void receiptCoversTimestampMethodPathAndBody() throws Exception {
        byte[] body = "{\"note\":\"已联系供应商\"}".getBytes(StandardCharsets.UTF_8);
        String timestamp = "1764197553";
        String path = "/mgr/jvs-aps/shortage/alerts/a5b4c6d8-7f9a-42a4-8f35-0a1b2c3d4e6f/ack";
        String signature = AlertReceipts.sign(SECRET, timestamp, "POST", path, body);

        assertTrue(AlertReceipts.verify(List.of(SECRET), timestamp, "POST", path, body, signature));
        // 登记了多个接收端时，任一密钥签出的回执都算本租户的。
        assertTrue(AlertReceipts.verify(List.of("other", SECRET), timestamp, "POST", path, body, signature));
        // 换动作、换预警、改正文、换密钥都不能复用同一个签名。
        assertFalse(AlertReceipts.verify(List.of(SECRET), timestamp, "POST", path.replace("ack", "mute"), body, signature));
        assertFalse(AlertReceipts.verify(List.of(SECRET), timestamp, "GET", path, body, signature));
        assertFalse(AlertReceipts.verify(List.of(SECRET), timestamp, "POST", path, "{}".getBytes(StandardCharsets.UTF_8), signature));
        assertFalse(AlertReceipts.verify(List.of("other"), timestamp, "POST", path, body, signature));
        assertFalse(AlertReceipts.verify(List.of(), timestamp, "POST", path, body, signature));
        assertFalse(AlertReceipts.verify(List.of(SECRET), timestamp, "POST", path, body, null));
    }

    @Test
    void staleOrMalformedReceiptTimestampsAreRejected() {
        long now = 1_764_200_000_000L;
        long tolerance = 300;
        assertTrue(AlertReceipts.fresh(String.valueOf(now / 1000), now, tolerance));
        assertTrue(AlertReceipts.fresh(String.valueOf(now / 1000 - 300), now, tolerance));
        assertTrue(AlertReceipts.fresh(String.valueOf(now / 1000 + 300), now, tolerance));
        // 超出容差：重放旧回执。
        assertFalse(AlertReceipts.fresh(String.valueOf(now / 1000 - 301), now, tolerance));
        assertFalse(AlertReceipts.fresh(String.valueOf(now / 1000 + 301), now, tolerance));
        // 缺失、毫秒级、非数字。
        assertFalse(AlertReceipts.fresh(null, now, tolerance));
        assertFalse(AlertReceipts.fresh(String.valueOf(now), now, tolerance));
        assertFalse(AlertReceipts.fresh("not-a-timestamp", now, tolerance));
    }

    @Test
    void receiptCanonicalFormIsStable() {
        String bodyHash = AlertReceipts.sha256Hex("{}".getBytes(StandardCharsets.UTF_8));
        assertEquals("1764197553\nPOST\n/shortage/alerts/x/ack\n" + bodyHash,
                AlertReceipts.canonical("1764197553", "POST", "/shortage/alerts/x/ack", "{}".getBytes(StandardCharsets.UTF_8)));
        // 空正文也参与签名。
        assertEquals("1764197553\nPOST\n/ack\n" + AlertReceipts.sha256Hex(new byte[0]),
                AlertReceipts.canonical("1764197553", "POST", "/ack", null));
    }
}
