package cn.bctools.aps.entity.dto;

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
@ApiModel("工序——可用的辅资源")
public class ProcessUseAuxiliaryResourcesDTO {
    @ApiModelProperty(value = "资源id", required = true)
    @NotBlank(message = "未选择辅资源")
    private String id;
}
