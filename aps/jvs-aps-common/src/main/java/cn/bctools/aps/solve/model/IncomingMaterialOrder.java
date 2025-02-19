package cn.bctools.aps.solve.model;

import cn.bctools.aps.annotation.Comment;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * @author jvs
 * 来料订单
 */
@Data
public class IncomingMaterialOrder {
    @Comment("物料id")
    private String materialId;

    @Comment("物料编码")
    private String materialCode;

    @Comment("数量")
    private BigDecimal quantity;

    @Comment("预计到货时间")
    private LocalDateTime deliveryTime;
}
