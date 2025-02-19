package cn.bctools.aps.entity.dto.plan;

import cn.bctools.aps.annotation.Comment;
import cn.bctools.aps.entity.enums.OrderSchedulingStatusEnum;
import io.swagger.annotations.ApiModel;
import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * @author jvs
 */
@Data
@Accessors(chain = true)
@ApiModel("排产结果冗余生产订单信息")
public class PlanOrderInfoDTO {
    @Comment(value = "订单号")
    private String code;

    @Comment(value = "物料id")
    private String materialId;

    @Comment(value = "物料编码")
    private String materialCode;

    @Comment(value = "需求数量")
    private BigDecimal quantity;

    @Comment(value = "需求交付时间")
    private LocalDateTime deliveryTime;

    @Comment(value = "排产状态")
    private OrderSchedulingStatusEnum schedulingStatus;

    @Comment(value = "是否为补充生产订单", notes = "false-否, true-是")
    private Boolean supplement;

    @Comment(value = "父订单编码（如果是补充订单，则指向其是那个订单的补充订单")
    private String parentOrderCode;

    @Comment(value = "颜色")
    private String color;
}
