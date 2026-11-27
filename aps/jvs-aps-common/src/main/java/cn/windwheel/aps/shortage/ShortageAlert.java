package cn.windwheel.aps.shortage;

import cn.windwheel.aps.shortage.ShortageModel.AlertState;
import cn.windwheel.aps.shortage.ShortageModel.Dimension;
import cn.windwheel.aps.shortage.ShortageModel.Severity;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

/**
 * 预警台账的一行。alertId 由系统签发，回执与外部幂等都以它为准。
 */
@Data
@Accessors(chain = true)
public class ShortageAlert {
    private String tenantId;
    private String alertId;
    /** {ruleCode}:{dimension}:{entityKey}:{bucket} */
    private String alertKey;
    /** 关联的欠料条目；数据质量等系统级预警没有欠料条目。 */
    private String shortageKey;
    private ShortageRule rule;
    private Dimension dimension;
    private String entityKey;
    /** 时间桶：P1、P2 小时，P3、P4 天。 */
    private String bucket;
    private Severity severity;
    /** 服务端模板生成，各渠道文案一致。 */
    private String title;
    /** JSON 对象原文：推送信封里的 payload。 */
    private String payload;
    private AlertState state;
    /** 静默期内的重复观测次数。台账要能看到“本条已观测 17 次”，否则会误以为只发生过一次。 */
    private int observedCount = 1;
    private LocalDateTime firstObservedAt;
    private LocalDateTime lastObservedAt;
    private LocalDateTime lastPushedAt;
    private int pushedCount;
    private String acknowledgedBy;
    private LocalDateTime acknowledgedAt;
    private String resolutionNote;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
