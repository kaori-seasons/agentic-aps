package cn.bctools.aps.dto;

import cn.bctools.aps.annotation.WorkingModeFormat;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.experimental.Accessors;

import javax.validation.constraints.NotBlank;

/**
 * @author jvs
 */
@Data
@Accessors(chain = true)
@ApiModel("新增工作模式")
public class SaveWorkModeDTO {
    @ApiModelProperty(value = "模式名称", required = true)
    @NotBlank(message = "模式名称不能为空")
    private String name;

    @ApiModelProperty(value = "工作时间。 多个工作时间以分号分割")
    @WorkingModeFormat(message = "工作时间格式错误")
    private String workingMode;
}
