package cn.bctools.aps.vo;

import cn.bctools.aps.entity.dto.ProcessUseAuxiliaryResourcesDTO;
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
@ApiModel("工序——可用的辅资源")
public class ProcessUseAuxiliaryResourcesVO extends ProcessUseAuxiliaryResourcesDTO {
    @ApiModelProperty(value = "资源编码")
    private String code;

    @ApiModelProperty(value = "名称")
    private String name;
}
