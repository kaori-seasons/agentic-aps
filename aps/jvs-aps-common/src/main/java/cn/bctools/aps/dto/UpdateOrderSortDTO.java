package cn.bctools.aps.dto;

import cn.bctools.aps.enums.OrderSortPositionEnum;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.experimental.Accessors;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

/**
 * @author jvs
 */
@Data
@Accessors(chain = true)
@ApiModel("修改订单顺序")
public class UpdateOrderSortDTO {
    @ApiModelProperty(value = "被移动的订单id", required = true)
    @NotBlank(message = "请选择要移动的订单")
    private String moveOrderId;

    @ApiModelProperty(value = "目标订单id", required = true)
    @NotBlank(message = "请选择目标订单")
    private String targetOrderId;

    @ApiModelProperty(value = "移动位置", required = true)
    @NotNull(message = "移动位置不能为空")
    private OrderSortPositionEnum position;
}
