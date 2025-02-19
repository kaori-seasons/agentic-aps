package cn.bctools.aps.dto;

import cn.bctools.aps.entity.enums.OrderSchedulingStatusEnum;
import cn.bctools.aps.entity.enums.OrderStatusEnum;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

/**
 * @author jvs
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@ApiModel("分页查询生产订单")
public class PageProductionOrderDTO extends PageBaseDTO {
    @ApiModelProperty(value = "订单号")
    private String code;

    @ApiModelProperty(value = "订单状态")
    private OrderStatusEnum orderStatus;

    @ApiModelProperty(value = "排产状态")
    private OrderSchedulingStatusEnum schedulingStatus;
}
