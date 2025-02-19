package cn.bctools.aps.vo;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * @author jvs
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@ApiModel("订单关系详情")
public class DetailProductionOrderRelatedVO {
    @ApiModelProperty(value = "订单")
    DetailProductionOrderVO order;

    @ApiModelProperty(value = "关联订单")
    List<DetailProductionOrderVO> relatedOrders;
}
