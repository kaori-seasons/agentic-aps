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
@ApiModel("修改工作模式")
public class UpdateWorkModeDTO extends SaveWorkModeDTO {
    @ApiModelProperty(value = "id", required = true)
    @NotBlank(message = "工作模式id不能为空")
    private String id;
}
