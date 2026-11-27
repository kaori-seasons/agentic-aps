package cn.windwheel.aps.shortage;

import cn.windwheel.aps.shortage.ShortageModel.Channel;
import cn.windwheel.aps.shortage.ShortageModel.Severity;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 推送信封。复用接入方案的通用信封，messageType 取 ALERT。
 * messageId 每次投递重新生成，只用于追踪；alertId 才是接收端的幂等键。
 */
@Component
@RequiredArgsConstructor
public class AlertEnvelope {
    public static final String VERSION = "1.0";
    public static final String SIGNATURE_HEADER = "X-Aps-Signature";
    public static final String TIMESTAMP_HEADER = "X-Aps-Timestamp";

    private final ObjectMapper json;
    private final ShortageProperties props;

    /**
     * @param ackUrl 回执链接，只在渠道支持回执且配置了对外基地址时下发
     */
    public String build(ShortageAlert alert, String messageId, Channel channel, String ackUrl) {
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("envelopeVersion", VERSION);
        envelope.put("messageId", messageId);
        envelope.put("messageType", "ALERT");
        envelope.put("sourceSystem", "APS");
        envelope.put("sourceInstance", props.resolveSourceInstance());
        envelope.put("tenantId", alert.getTenantId());
        envelope.put("alertKey", alert.getAlertKey());
        envelope.put("alertId", alert.getAlertId());
        envelope.put("occurredAt", ShortageText.iso(alert.getFirstObservedAt(), props.zone()));
        Map<String, Object> payload;
        try {
            payload = json.readValue(alert.getPayload(), json.getTypeFactory().constructMapType(LinkedHashMap.class,
                    String.class, Object.class));
        } catch (Exception e) {
            throw new IllegalStateException("预警载荷无法解析 alertId=" + alert.getAlertId(), e);
        }
        if (ackUrl != null) {
            payload.put("ackUrl", ackUrl);
        }
        envelope.put("payload", payload);
        try {
            return json.writeValueAsString(envelope);
        } catch (Exception e) {
            throw new IllegalStateException("预警信封无法序列化 alertId=" + alert.getAlertId(), e);
        }
    }

    /** 不配置对外基地址就不下发，绝不把内网或本机地址发给外部渠道。 */
    public String ackUrl(String alertId) {
        String base = props.getPublicBaseUrl();
        return base == null || base.isBlank() ? null : base.replaceAll("/+$", "") + "/shortage/alerts/" + alertId + "/ack";
    }

    /** 仅 P1、P2 走邮件，与方案的通道表一致。 */
    public static boolean mailEligible(Channel channel, Severity severity) {
        return channel == Channel.EMAIL && severity != null && severity.atLeast(Severity.P2);
    }

    public static String newMessageId() {
        return UUID.randomUUID().toString();
    }
}
