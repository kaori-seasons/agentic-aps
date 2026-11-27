package cn.windwheel.aps.shortage;

import cn.windwheel.aps.shortage.ShortageModel.Channel;
import cn.windwheel.aps.shortage.ShortageModel.NotifyState;
import cn.windwheel.aps.shortage.ShortageModel.Receipt;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

/**
 * 一次推送记录，兼回执审计。外部渠道以 alertId 去重，同一 alertId 重复投递必须幂等。
 */
@Data
@Accessors(chain = true)
public class ShortageNotify {
    private String tenantId;
    private String id;
    private String alertId;
    private Channel channel;
    /** 接收端：Webhook 地址、收件人列表，或台账与出站的固定标识。 */
    private String target;
    /** alertId:channel:sha256(target) 前 32 位，推送唯一键。 */
    private String idempotencyKey;
    private NotifyState state;
    private int attempts;
    private String leaseToken;
    private LocalDateTime leaseUntil;
    private LocalDateTime availableAt;
    private LocalDateTime lastAttemptAt;
    private LocalDateTime sentAt;
    private Integer httpStatus;
    private String lastError;
    private Receipt receiptState;
    private String receiptNote;
    private String receiptBy;
    private LocalDateTime receiptAt;
    /** 投递的信封原文。 */
    private String payload;
    private LocalDateTime createdAt;
}
