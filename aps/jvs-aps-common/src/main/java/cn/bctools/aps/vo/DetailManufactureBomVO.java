package cn.bctools.aps.vo;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * @author jvs
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@ApiModel("制造BOM详情")
public class DetailManufactureBomVO {

    @ApiModelProperty(value = "bom id")
    private String id;

    @ApiModelProperty(value = "父件物料id")
    private String materialId;

    @ApiModelProperty(value = "父件物料编码")
    private String materialCode;

    @ApiModelProperty(value = "父件物料名")
    private String materialName;

    @ApiModelProperty(value = "子件物料")
    private List<MaterialBomVO> childMaterials;
}
