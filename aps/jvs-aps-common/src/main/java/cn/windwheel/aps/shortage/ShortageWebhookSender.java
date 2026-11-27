package cn.windwheel.aps.shortage;

import cn.windwheel.aps.shortage.ShortageProperties.Endpoint;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;

/**
 * 通用 webhook 投递：POST JSON，HMAC-SHA256 签名，连接 3 秒、整次 10 秒，2xx 算成功。
 * 不跟随重定向：3xx 说明地址配置错了，按失败处理并暴露出来，比静默跟到别处更安全。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ShortageWebhookSender {
    public static final String SIGNATURE_PREFIX = "sha256=";

    private final ShortageProperties props;
    private HttpClient client;

    @PostConstruct
    void init() {
        ShortageProperties.Webhook config = props.getWebhook();
        client = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(config.getConnectTimeoutSeconds()))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    public record Delivery(boolean accepted, Integer status, String detail) {
        static Delivery failed(Integer status, String detail) {
            return new Delivery(false, status, detail);
        }
    }

    public Delivery send(Endpoint endpoint, String body) {
        String signature;
        try {
            signature = sign(endpoint.getSecret(), body);
        } catch (Exception e) {
            return Delivery.failed(null, "签名失败：" + e.getMessage());
        }
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(endpoint.getUrl()))
                    .timeout(Duration.ofSeconds(props.getWebhook().getRequestTimeoutSeconds()))
                    .header("Content-Type", "application/json; charset=utf-8")
                    .header(AlertEnvelope.SIGNATURE_HEADER, signature)
                    .header(AlertEnvelope.TIMESTAMP_HEADER, String.valueOf(System.currentTimeMillis()))
                    .header("User-Agent", "aps-shortage-alert/1.0")
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                    .build();
            HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
            try (InputStream stream = response.body()) {
                drain(stream);
            }
            int status = response.statusCode();
            if (status >= 200 && status < 300) {
                return new Delivery(true, status, null);
            }
            // 不把响应正文带回库：渠道的错误页可能很大，也可能含敏感信息。
            return Delivery.failed(status, "接收端返回 HTTP " + status);
        } catch (IOException e) {
            return Delivery.failed(null, "接收端不可达：" + e.getClass().getSimpleName());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Delivery.failed(null, "投递被中断");
        } catch (IllegalArgumentException e) {
            return Delivery.failed(null, "接收端地址无效：" + e.getMessage());
        }
    }

    /** 把响应读掉再关流，连接才能被复用；最多读到上限为止。 */
    private void drain(InputStream stream) throws IOException {
        byte[] buffer = new byte[4096];
        long budget = props.getWebhook().getMaxResponseBytes();
        while (budget > 0 && stream.read(buffer) != -1) {
            budget -= buffer.length;
        }
    }

    /** 请求头 X-Aps-Signature 的值：sha256=Hex(HMAC-SHA256(secret, body))。 */
    public static String sign(String secret, String body) throws Exception {
        if (secret == null || secret.isEmpty()) {
            throw new IllegalArgumentException("接收端未配置签名密钥");
        }
        return SIGNATURE_PREFIX + HexFormat.of().formatHex(hmac(secret, body));
    }

    /** 常量时间比较，不给时序攻击留口子。 */
    public static boolean verify(String secret, String body, String presented) {
        try {
            return presented != null && presented.startsWith(SIGNATURE_PREFIX) && presented.length() == 71
                    && MessageDigest.isEqual(hmac(secret, body),
                            HexFormat.of().parseHex(presented.substring(SIGNATURE_PREFIX.length())));
        } catch (Exception e) {
            return false;
        }
    }

    private static byte[] hmac(String secret, String body) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return mac.doFinal(body.getBytes(StandardCharsets.UTF_8));
    }
}
