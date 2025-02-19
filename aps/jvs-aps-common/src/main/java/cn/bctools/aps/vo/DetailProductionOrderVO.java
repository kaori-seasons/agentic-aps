package cn.bctools.aps.vo;

import cn.bctools.aps.entity.enums.OrderSchedulingStatusEnum;
import cn.bctools.aps.entity.enums.OrderStatusEnum;
import cn.bctools.aps.entity.enums.OrderTypeEnum;
import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * @author jvs
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@ApiModel("生产订单详情")
public class DetailProductionOrderVO extends BaseVO {
    @ApiModelProperty(value = "订单号")
    private String code;

    @ApiModelProperty(value = "物料编码")
    private String materialCode;

    @ApiModelProperty(value = "需求数量")
    private BigDecimal quantity;

    @ApiModelProperty(value = "需求交付时间")
    @JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime deliveryTime;

    @ApiModelProperty(value = "优先级")
    private Integer priority;

    @ApiModelProperty(value = "订单类型")
    private OrderTypeEnum type;

    @ApiModelProperty(value = "订单状态")
    private OrderStatusEnum orderStatus;

    @ApiModelProperty(value = "排产状态")
    private OrderSchedulingStatusEnum schedulingStatus;

    @ApiModelProperty(value = "显示颜色")
    private String color;

    @ApiModelProperty(value = "true-参与排产，false-不参与排产")
    private Boolean canSchedule;

    @ApiModelProperty(value = "true-有补充生产订单，false-没有补充生产订单")
    private Boolean hasSupplement;
 
}
