package cn.bctools.aps.vo;

import cn.bctools.aps.entity.dto.ProcessUseMaterialsDTO;
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
@ApiModel("工序——使用的物料")
public class ProcessUseMaterialsVO extends ProcessUseMaterialsDTO {
    @ApiModelProperty(value = "物料编码")
    private String code;

    @ApiModelProperty(value = "名称")
    private String name;
}
