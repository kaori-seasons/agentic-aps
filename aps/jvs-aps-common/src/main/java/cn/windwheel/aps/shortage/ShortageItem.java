package cn.windwheel.aps.shortage;

import cn.windwheel.aps.shortage.ShortageModel.Dimension;
import cn.windwheel.aps.shortage.ShortageModel.ItemState;
import cn.windwheel.aps.shortage.ShortageModel.Severity;
import cn.windwheel.aps.shortage.ShortageModel.Source;
import cn.windwheel.aps.shortage.ShortageModel.SupplyMode;
import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 欠料清单的一行，同时是 jvs_shortage_item 的落库结构。JSON 列在这里保持原文，对外输出时再解析。
 */
@Data
@Accessors(chain = true)
public class ShortageItem {
    private String tenantId;
    /** 业务自然键，不含运行ID。ORDER:{订单编码}:{物料编码} / WEEK:{地点}:{物料}:{规则码}:{引用}[:{周一}] */
    private String shortageKey;
    private Dimension dimension;
    private Source source;
    /** 最近一次观测到该缺口的求解审计ID或补货运行ID。 */
    private String runId;
    private LocalDateTime detectedAt;
    private LocalDateTime firstDetectedAt;
    /** 所依据的数据截止时间。 */
    private LocalDateTime dataCutoff;
    /** 当前评估的最高等级；未命中任何规则时为空，不是 P4。 */
    private Severity severity;
    /** 当前最高等级命中的规则码；未命中时是观测类别。 */
    private String reasonCode;
    private String materialId;
    private String materialCode;
    private String materialName;
    private String unit;
    private BigDecimal gapQuantity;
    /** 毛需求，只有求解侧有。 */
    private BigDecimal requiredQuantity;
    private LocalDateTime requiredAt;
    private LocalDate weekStart;
    private String orderCode;
    private String locationCode;
    private String reference;
    private String sourceReason;
    /** 轮到本订单时的在库量，缺口归属依据。 */
    private BigDecimal onHandAvailable;
    private BigDecimal onHandConsumed = BigDecimal.ZERO;
    private BigDecimal inTransitConsumed = BigDecimal.ZERO;
    private BigDecimal inTransitSkipped = BigDecimal.ZERO;
    /** JSON 数组原文：被跳过的在途行与参与分配的在途行。 */
    private String skippedLines;
    private String availableInTransit;
    private SupplyMode supplyMode;
    /** 无到货计划时为空，此时 supplyMode 必须是 NONE。 */
    private LocalDateTime expectedEta;
    private String supplierCode;
    private LocalDateTime earliestStart;
    /** ISO-8601 时长。 */
    private String delayDuration;
    private String downstreamTasks;
    private BigDecimal safetyStock;
    private BigDecimal scenarioBalance;
    /** JSON 对象原文：归属依据等补充信息。 */
    private String detail;
    private ItemState state;
    private int observedCount = 1;
    private LocalDateTime lastEvaluatedAt;
    private String resolvedBy;
    private LocalDateTime resolvedAt;
    private String resolutionNote;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
