package cn.windwheel.aps.shortage;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * 渠道回调回执的签名约定。方案只定了出向签名，漏了回执的入向认证；没有它，任何人都能伪造“已确认”。
 * <p>
 * 签名内容把时间戳、方法、路径、正文摘要都纳入，并与接收端共用同一密钥：
 * Canonical = timestamp + "\n" + METHOD + "\n" + path + "\n" + sha256Hex(body)
 * X-Aps-Signature = sha256=Hex(HMAC-SHA256(secret, Canonical))
 * 时间戳超出容差的请求一律拒绝，堵住重放。
 */
public final class AlertReceipts {
    public static final String SIGNATURE_HEADER = AlertEnvelope.SIGNATURE_HEADER;
    public static final String TIMESTAMP_HEADER = "X-Aps-Receipt-Timestamp";

    private AlertReceipts() {
    }

    public static String canonical(String timestamp, String method, String path, byte[] body) {
        return timestamp + "\n" + method + "\n" + path + "\n" + sha256Hex(body == null ? new byte[0] : body);
    }

    public static boolean fresh(String timestamp, long nowMillis, long toleranceSeconds) {
        if (timestamp == null || !timestamp.matches("\\d{10}")) {
            return false;
        }
        // 只接受秒级时间戳：十位数字，毫秒级的写法直接判非法，避免两种单位混用造成千倍偏差。
        long issued = Long.parseLong(timestamp) * 1000;
        return Math.abs(nowMillis - issued) <= toleranceSeconds * 1000;
    }

    /** 用给定密钥逐个校验，任一通过即认为来自本租户登记的接收端。 */
    public static boolean verify(Iterable<String> secrets, String timestamp, String method, String path, byte[] body,
                                 String presented) {
        if (secrets == null || presented == null) {
            return false;
        }
        String canonical = canonical(timestamp, method, String.valueOf(path), body);
        for (String secret : secrets) {
            if (secret != null && !secret.isEmpty() && ShortageWebhookSender.verify(secret, canonical, presented)) {
                return true;
            }
        }
        return false;
    }

    static String sha256Hex(byte[] body) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(body));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /** 仅供测试与文档示例：按约定生成签名头。 */
    public static String sign(String secret, String timestamp, String method, String path, byte[] body) throws Exception {
        return ShortageWebhookSender.sign(secret, canonical(timestamp, method, path, body));
    }

    static byte[] bytes(String text) {
        return text == null ? new byte[0] : text.getBytes(StandardCharsets.UTF_8);
    }
}
