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
@ApiModel("修改资源")
public class UpdateProductionResourceDTO extends SaveProductionResourceDTO {
    @ApiModelProperty(value = "id", required = true)
    @NotBlank(message = "资源id不能为空")
    private String id;
}
