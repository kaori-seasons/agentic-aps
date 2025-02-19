package cn.bctools.aps.dto;

import cn.bctools.aps.entity.enums.OrderSchedulingStatusEnum;
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
@ApiModel("分页查询待排产的生产订单")
public class PagePlanningProductionOrderPendingDTO extends PageBaseDTO {
    @ApiModelProperty(value = "订单号")
    private String code;

    @ApiModelProperty(value = "排产状态")
    private OrderSchedulingStatusEnum schedulingStatus;

    @ApiModelProperty(value = "排除的订单id")
    private String excludeId;
}
