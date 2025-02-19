package cn.bctools.aps.dto;

import cn.bctools.aps.entity.enums.IncomingMaterialOrderStatusEnum;
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
@ApiModel("分页查询来料订单")
public class PageIncomingMaterialOrderDTO extends PageBaseDTO {
    @ApiModelProperty(value = "订单号")
    private String code;

    @ApiModelProperty(value = "物料编码")
    private String materialCode;

    @ApiModelProperty(value = "状态")
    private IncomingMaterialOrderStatusEnum orderStatus;
}
