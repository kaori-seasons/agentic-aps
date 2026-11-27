package cn.windwheel.aps.shortage;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 缺料预警配置。接收端与密钥只放配置文件，不落库：密钥入库需要再做一层加密与脱敏，
 * 而现状的账号体系本身就维护在配置里，两者口径一致。密钥用环境变量占位符注入，不要写进仓库。
 */
@Data
@Component
@ConfigurationProperties(prefix = "aps.shortage")
public class ShortageProperties {
    /** 是否启用后台轮询：投递推送与周期复评。关掉后仍可通过 POST /shortage/scan 手工触发。 */
    private boolean workerEnabled = true;
    /** 投递轮询间隔毫秒。 */
    private long pollMs = 2000;
    /** 周期复评间隔毫秒。时间推移会让缺口跨过冻结、三日、十四日边界，必须周期性重评才能升级。 */
    private long sweepMs = 300000;
    /** 每次投递最多认领的条数。 */
    private int dispatchBatch = 200;
    /** 每次复评最多处理的欠料条目数。 */
    private int sweepBatch = 500;
    /**
     * 线上时间的时区偏移。缺省取 JVM 默认时区：业务侧统一用 LocalDateTime，库端不存时区，
     * 这里若与 JVM 时区不一致，对外信封里的偏移量就是错的。
     */
    private String zoneId;
    /** 对外信封的 sourceInstance，缺省取主机名。 */
    private String sourceInstance;
    /**
     * 生成回执链接 ackUrl 用的对外基地址，例如 https://aps.example.local/mgr/jvs-aps。
     * 不配置就不下发 ackUrl，绝不把内网或本机地址发给外部渠道。
     */
    private String publicBaseUrl;
    /** 签名回执允许的最大时钟偏差秒数。 */
    private long receiptToleranceSeconds = 300;
    /** 拉取出站通道：给 ERP、SRM 拉结构化缺料清单。没人拉取时条目会一直堆在待投递，所以默认关闭。 */
    private boolean outboxEnabled = false;
    private Webhook webhook = new Webhook();
    private List<Endpoint> webhooks = new ArrayList<>();
    private Mail mail = new Mail();
    /** 角色编码到邮箱列表。规则上的接收角色是编码，邮箱是另一套东西，RBAC 落地前只能静态维护。 */
    private Map<String, List<String>> roleRecipients = new LinkedHashMap<>();

    @Data
    public static class Webhook {
        private int connectTimeoutSeconds = 3;
        private int requestTimeoutSeconds = 10;
        /** 单接收端每分钟最多投递条数，超出排队不丢。 */
        private int perMinute = 60;
        /** 失败退避重试间隔分钟数。用尽后进 DEAD 并告警。 */
        private List<Integer> retryBackoffMinutes = new ArrayList<>(List.of(1, 5, 25));
        /** 投递租约秒数。进程在投递中途崩溃，租约过期后条目会被重新认领。 */
        private int leaseSeconds = 60;
        private int maxResponseBytes = 4096;
    }

    @Data
    public static class Endpoint {
        /** 名称，只用于日志与排错。 */
        private String name;
        /** 生效租户，留空表示全部租户。 */
        private String tenantId;
        private String url;
        /** HMAC-SHA256 密钥，同时用于出向签名与回执校验。 */
        private String secret;
        private boolean enabled = true;
    }

    @Data
    public static class Mail {
        /** 需要额外引入邮件依赖并配置 spring.mail.* 后才能打开。 */
        private boolean enabled = false;
        private String from;
    }

    private volatile String resolvedInstance;

    /** 信封里的 sourceInstance：未配置时取主机名，多工厂部署靠它区分来源实例。 */
    public String resolveSourceInstance() {
        if (resolvedInstance == null) {
            resolvedInstance = sourceInstance != null && !sourceInstance.isBlank() ? sourceInstance.trim() : hostName();
        }
        return resolvedInstance;
    }

    private static String hostName() {
        try {
            return java.net.InetAddress.getLocalHost().getHostName();
        } catch (java.net.UnknownHostException e) {
            return "unknown";
        }
    }

    public ZoneId zone() {
        return zoneId == null || zoneId.isBlank() ? ZoneId.systemDefault() : ZoneId.of(zoneId);
    }

    public Duration backoff(int attempts) {
        // attempts 是已经失败的次数，取第 attempts 档退避；用尽即表示不再重试。
        if (attempts < 1 || attempts > webhook.getRetryBackoffMinutes().size()) {
            return null;
        }
        return Duration.ofMinutes(webhook.getRetryBackoffMinutes().get(attempts - 1));
    }
}
