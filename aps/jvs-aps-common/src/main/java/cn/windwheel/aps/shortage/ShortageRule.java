package cn.windwheel.aps.shortage;

import cn.windwheel.aps.shortage.ShortageModel.Channel;
import cn.windwheel.aps.shortage.ShortageModel.Dimension;
import cn.windwheel.aps.shortage.ShortageModel.Severity;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 预警规则目录。这里只放默认值，租户在 jvs_shortage_rule 里的覆盖值优先。
 * <p>
 * 默认阈值来自通用生产节奏，不是任何工厂的事实数据，上线前必须按实际节奏标定。
 * 接收角色用现有角色编码：计划员 APS_SCHEDULER、采购 APS_PURCHASER、物控 APS_DEMAND_PLANNER、
 * 计划经理 APS_PLAN_MANAGER、运维 APS_OPS。角色到人的映射在 RBAC 落地前只能静态维护。
 */
@Getter
public enum ShortageRule {
    MRP_GAP_FROZEN(Dimension.ORDER, Severity.P1, 1, "DAYS", "APS_SCHEDULER,APS_PURCHASER",
            "LEDGER,WEBHOOK,EMAIL,OUTBOX", "立即催料或改期，准备分批"),
    MRP_GAP_3D(Dimension.ORDER, Severity.P2, 3, "DAYS", "APS_PURCHASER",
            "LEDGER,WEBHOOK,EMAIL,OUTBOX", "催料并确认到货"),
    MRP_GAP_14D(Dimension.ORDER, Severity.P3, 14, "DAYS", "APS_PURCHASER",
            "LEDGER,WEBHOOK,OUTBOX", "纳入催料清单"),
    /** 最要紧的一条：手上有在途，但到得太晚被扣减跳过，过去完全看不见。 */
    MRP_ETA_PASSED(Dimension.ORDER, Severity.P2, null, null, "APS_PURCHASER,APS_SCHEDULER",
            "LEDGER,WEBHOOK,EMAIL", "核实到货时间，或改交期，或分批"),
    MRP_NO_SUPPLY(Dimension.ORDER, Severity.P1, null, null, "APS_PURCHASER",
            "LEDGER,WEBHOOK,EMAIL,OUTBOX", "无在途也无采购建议，立即下单"),
    MRP_LEADTIME_VIOLATION(Dimension.ORDER, Severity.P1, null, null, "APS_PURCHASER",
            "LEDGER,WEBHOOK,EMAIL", "在途全部晚于交期，加急或改期"),
    MRP_RELEASE_BLOCKED(Dimension.ORDER, Severity.P1, null, null, "APS_PLAN_MANAGER",
            "LEDGER,WEBHOOK,EMAIL", "重新同步后放行"),
    DEMAND_SHORTAGE(Dimension.WEEK, Severity.P2, null, null, "APS_PURCHASER,APS_DEMAND_PLANNER",
            "LEDGER,WEBHOOK,OUTBOX", "走补货审批"),
    DEMAND_BELOW_SAFETY(Dimension.WEEK, Severity.P3, null, null, "APS_DEMAND_PLANNER",
            "LEDGER,WEBHOOK,OUTBOX", "关注，不强制动作"),
    DEMAND_STALE_ETA(Dimension.WEEK, Severity.P2, null, null, "APS_PURCHASER",
            "LEDGER,WEBHOOK,EMAIL", "核实交期或取消余量"),
    DEMAND_PROPOSAL_BLOCKED(Dimension.WEEK, Severity.P3, null, null, "APS_PURCHASER",
            "LEDGER,WEBHOOK", "调整起订量、库容或预算"),
    DEMAND_PENDING_RECONCILE(Dimension.WEEK, Severity.P3, 1440, "MINUTES", "APS_DEMAND_PLANNER",
            "LEDGER,WEBHOOK", "完成对账"),
    /**
     * 下面两条的数据源是外部同步通道的批次表与死信表。该通道落地前没有数据可评，
     * 所以默认关闭；通道上线后再打开，届时按配置的阈值评估。
     */
    DATA_STALE(Dimension.SYSTEM, Severity.P4, 120, "MINUTES", "APS_OPS", "LEDGER", "核查数据同步水位", false),
    DATA_SNAPSHOT_EXPIRED(Dimension.SYSTEM, Severity.P4, 1440, "MINUTES", "APS_OPS,APS_SCHEDULER",
            "LEDGER,WEBHOOK", "重新求解以刷新欠料依据"),
    SYNC_DEAD_LETTER(Dimension.SYSTEM, Severity.P4, 30, "MINUTES", "APS_OPS", "LEDGER,WEBHOOK",
            "处理同步死信", false),
    DATA_GAP_UNATTRIBUTABLE(Dimension.SYSTEM, Severity.P4, null, null, "APS_OPS", "LEDGER", "核查欠料归属"),
    /**
     * 推送重试用尽后的元预警。只进台账，不再外推：外推同一个失效渠道只会再失败一次并自我循环。
     */
    NOTIFY_DELIVERY_FAILED(Dimension.SYSTEM, Severity.P2, null, null, "APS_OPS", "LEDGER", "检查推送接收端");

    public static final int DEFAULT_COOLDOWN_MINUTES = 60;

    private final Dimension dimension;
    private final Severity severity;
    private final Integer thresholdValue;
    private final String thresholdUnit;
    private final String receivers;
    private final String channels;
    private final String actionHint;
    private final boolean enabled;

    ShortageRule(Dimension dimension, Severity severity, Integer thresholdValue, String thresholdUnit,
                 String receivers, String channels, String actionHint) {
        this(dimension, severity, thresholdValue, thresholdUnit, receivers, channels, actionHint, true);
    }

    ShortageRule(Dimension dimension, Severity severity, Integer thresholdValue, String thresholdUnit,
                 String receivers, String channels, String actionHint, boolean enabled) {
        this.dimension = dimension;
        this.severity = severity;
        this.thresholdValue = thresholdValue;
        this.thresholdUnit = thresholdUnit;
        this.receivers = receivers;
        this.channels = channels;
        this.actionHint = actionHint;
        this.enabled = enabled;
    }

    public boolean isSystem() {
        return dimension == Dimension.SYSTEM;
    }

    public List<String> channelList() {
        return split(channels);
    }

    public List<String> receiverList() {
        return split(receivers);
    }

    /** 时间桶随等级走，P1、P2 按小时，P3、P4 按天。 */
    public static ShortageRule parse(String code) {
        for (ShortageRule rule : values()) {
            if (rule.name().equals(code)) {
                return rule;
            }
        }
        return null;
    }

    static List<String> split(String value) {
        return value == null || value.isBlank() ? Collections.emptyList()
                : Arrays.stream(value.split(",")).map(String::trim).filter(s -> !s.isEmpty()).collect(Collectors.toList());
    }

    /**
     * 生效规则：默认值叠加租户覆盖。时长类阈值换算成分钟，天数类保留天数。
     */
    public record Effective(ShortageRule rule, Severity severity, boolean enabled, Long thresholdMinutes,
                            Long thresholdDays, int cooldownMinutes, List<String> receivers,
                            List<String> channels, String actionHint) {
        public boolean allows(LocalDateTime lastCreatedAt, LocalDateTime now) {
            return lastCreatedAt == null || !lastCreatedAt.plusMinutes(cooldownMinutes).isAfter(now);
        }

        public boolean hasChannel(Channel channel) {
            return channels.contains(channel.name());
        }
    }
}
