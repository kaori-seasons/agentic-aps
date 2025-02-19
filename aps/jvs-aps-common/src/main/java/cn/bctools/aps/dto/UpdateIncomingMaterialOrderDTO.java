package cn.bctools.aps.dto;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import javax.validation.constraints.NotBlank;

/**
 * @author jvs
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@ApiModel("修改来料订单")
public class UpdateIncomingMaterialOrderDTO extends SaveIncomingMaterialOrderDTO{
    @ApiModelProperty(value = "id", required = true)
    @NotBlank(message = "来料订单id不能为空")
    private String id;
}
