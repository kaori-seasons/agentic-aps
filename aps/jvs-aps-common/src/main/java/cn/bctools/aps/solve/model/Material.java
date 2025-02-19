package cn.bctools.aps.solve.model;

import cn.bctools.aps.annotation.Comment;
import cn.bctools.aps.entity.enums.MaterialSourceEnum;
import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.time.Duration;

/**
 * @author jvs
 * 物料
 */
@Data
@Accessors(chain = true)
public class Material {
    @Comment("物料id")
    private String id;

    @Comment("编码")
    private String code;

    @Comment("名称")
    private String name;

    @Comment("来源")
    private MaterialSourceEnum source;

    @Comment("库存")
    private BigDecimal quantity;

    @Comment("安全库存")
    private BigDecimal safetyStock;

    @Comment("计量单位")
    private String unit;

    @Comment("提前期")
    private Duration leadTimeDuration;

    @Comment("缓冲期")
    private Duration bufferTimeDuration;

}
