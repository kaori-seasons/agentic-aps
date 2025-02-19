package cn.bctools.aps.vo;

import cn.bctools.aps.entity.dto.ProcessUseMainResourcesDTO;
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
@ApiModel("工序——可用的主资源")
public class ProcessUseMainResourcesVO extends ProcessUseMainResourcesDTO {
    @ApiModelProperty(value = "资源编码")
    private String code;

    @ApiModelProperty(value = "名称")
    private String name;
}
