package cn.bctools.aps.vo;

import cn.bctools.aps.entity.enums.IncomingMaterialOrderStatusEnum;
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
@ApiModel("来料订单详情")
public class DetailIncomingMaterialOrderVO extends BaseVO {
    @ApiModelProperty(value = "订单号")
    private String code;

    @ApiModelProperty(value = "物料编码")
    private String materialCode;

    @ApiModelProperty(value = "数量")
    private BigDecimal quantity;

    @ApiModelProperty(value = "预计到货时间")
    @JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime deliveryTime;

    @ApiModelProperty(value = "状态")
    private IncomingMaterialOrderStatusEnum orderStatus;
}
