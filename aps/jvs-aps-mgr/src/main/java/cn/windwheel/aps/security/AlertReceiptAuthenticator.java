package cn.windwheel.aps.security;

import cn.windwheel.aps.shortage.AlertReceipts;
import cn.windwheel.aps.shortage.ShortageAlertStore;
import cn.windwheel.aps.shortage.ShortageProperties;
import cn.windwheel.aps.shortage.ShortageProperties.Endpoint;
import cn.windwheel.common.model.AuthContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 预警回执的签名认证。渠道回调 ackUrl 时没有登录会话，靠与出向推送共用的接收端密钥证明身份。
 * <p>
 * alertId 是系统签发的随机 UUID，再加签名与时间戳容差，外部既伪造不了“已确认”，也重放不了旧回执。
 * 预警不存在与签名错误返回同一句话，不给探测 alertId 的机会。
 */
@Component
@RequiredArgsConstructor
public class AlertReceiptAuthenticator implements ServiceRequestAuthenticator {
    public static final String SERVICE_USER = "service:alert-receipt";
    public static final String SERVICE_ROLE = "APS_ALERT_CONNECTOR";
    static final Pattern RECEIPT = Pattern.compile("^/shortage/alerts/([0-9a-fA-F-]{36})/(ack|resolve|mute)$");
    private static final int MAX_BODY_BYTES = 64 * 1024;

    private final ShortageAlertStore alerts;
    private final ShortageProperties props;

    @Override
    public boolean supports(String method, String path) {
        return "POST".equals(method) && RECEIPT.matcher(path).matches();
    }

    @Override
    public ServiceAuthentication authenticate(HttpServletRequest request) {
        String contextPath = request.getContextPath();
        String uri = request.getRequestURI();
        Matcher matcher = RECEIPT.matcher(uri.substring(contextPath.length()));
        if (!matcher.matches()) {
            return null;
        }
        byte[] body;
        try (InputStream in = request.getInputStream()) {
            body = in.readNBytes(MAX_BODY_BYTES);
        } catch (IOException e) {
            throw new ServiceAuthException(400, "回执正文读取失败");
        }
        String timestamp = request.getHeader(AlertReceipts.TIMESTAMP_HEADER);
        if (!AlertReceipts.fresh(timestamp, System.currentTimeMillis(), props.getReceiptToleranceSeconds())) {
            throw new ServiceAuthException(401, "回执时间戳缺失或超出容差");
        }
        var alert = alerts.findByAlertId(matcher.group(1))
                .orElseThrow(() -> new ServiceAuthException(401, "回执签名无效"));
        List<String> secrets = props.getWebhooks().stream()
                .filter(Endpoint::isEnabled)
                .filter(e -> e.getTenantId() == null || e.getTenantId().isBlank()
                        || e.getTenantId().equals(alert.getTenantId()))
                .map(Endpoint::getSecret).toList();
        // 签名里的路径取请求行里的完整路径，即调用方实际请求的路径，不含查询串。
        if (!AlertReceipts.verify(secrets, timestamp, request.getMethod(), uri, body,
                request.getHeader(AlertReceipts.SIGNATURE_HEADER))) {
            throw new ServiceAuthException(401, "回执签名无效");
        }
        AuthContext context = new AuthContext()
                .setTenantId(alert.getTenantId())
                .setUserId(SERVICE_USER)
                .setUsername("预警回执服务账号")
                .setRoleCodes(List.of(SERVICE_ROLE))
                .setFactoryIds(List.of())
                .setWorkshopIds(List.of())
                .setResourceGroupIds(List.of());
        return new ServiceAuthentication(context, new CachedBodyRequest(request, body));
    }
}
