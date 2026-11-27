package cn.windwheel.aps.shortage;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;

/**
 * 缺料预警的基础枚举与共用值对象。
 * <p>
 * 两条预警线不合并：ORDER 短周期齐套按订单看，缺口是“订单需求扣在库与可用在途后仍有剩余”；
 * WEEK 长周期水位按周看，缺口是“场景水位低于安全库存”。两者对缺料的定义不同，台账必须带 dimension 区分。
 */
public final class ShortageModel {
    private ShortageModel() {
    }

    /**
     * SYSTEM 不是第三条预警线，而是不挂在任何缺料条目上的系统级预警：数据质量、发布阻断、推送失败。
     * 它们没有订单或周水位可归属，放进 ORDER 或 WEEK 都会让“按线分组”的口径失真。
     */
    public enum Dimension { ORDER, WEEK, SYSTEM }

    public enum Source { MRP, DEMAND }

    /**
     * 等级由规则决定。P1、P2 按小时分桶，P3、P4 按天分桶。
     */
    public enum Severity {
        P1(30, true), P2(120, true), P3(480, false), P4(1440, false);

        private final int responseMinutes;
        private final boolean hourlyBucket;

        Severity(int responseMinutes, boolean hourlyBucket) {
            this.responseMinutes = responseMinutes;
            this.hourlyBucket = hourlyBucket;
        }

        public int getResponseMinutes() {
            return responseMinutes;
        }

        public boolean atLeast(Severity other) {
            return other == null || ordinal() <= other.ordinal();
        }

        public boolean higherThan(Severity other) {
            return other != null && ordinal() < other.ordinal();
        }

        /** 同一桶内不重发：P1、P2 同一小时，P3、P4 同一天。 */
        public String bucket(LocalDateTime now) {
            return now.format(hourlyBucket ? DateTimeFormatter.ofPattern("yyyyMMdd'T'HH")
                    : DateTimeFormatter.ofPattern("yyyyMMdd"));
        }

        public static Severity parse(String value) {
            if (value == null) {
                return null;
            }
            for (Severity severity : values()) {
                if (severity.name().equals(value)) {
                    return severity;
                }
            }
            throw new IllegalArgumentException("未知预警等级：" + value);
        }

        /** 多条命中时取最高等级，对应的那条规则作为原因码。 */
        public static <T> T highest(List<T> matches, java.util.function.Function<T, Severity> severity) {
            return matches.stream().min(Comparator.comparingInt(m -> severity.apply(m).ordinal())).orElse(null);
        }
    }

    public enum ItemState { OPEN, ACKNOWLEDGED, RESOLVED, MUTED }

    /**
     * SUPERSEDED 是预警的终态：同一缺口在同一规则上升级或跨桶重发时，旧预警由新预警接续。
     */
    public enum AlertState { OPEN, ACKNOWLEDGED, RESOLVED, MUTED, SUPERSEDED }

    public enum NotifyState { PENDING, SENDING, SENT, DEAD, SKIPPED }

    public enum Channel { LEDGER, WEBHOOK, OUTBOX, EMAIL }

    public enum Receipt { ACK, RESOLVED, MUTED, REJECTED }

    /**
     * NONE 必须显式呈现为“无到货计划”，不能省略 expectedEta 了事，否则计划员会误以为到货时间待定。
     */
    public enum SupplyMode { IN_TRANSIT, IN_TRANSIT_LATE, PURCHASE_PROPOSAL, NONE }

    public static final String SYSTEM_ACTOR = "system";
    public static final String WORKER_ACTOR = "system:shortage-worker";
}
