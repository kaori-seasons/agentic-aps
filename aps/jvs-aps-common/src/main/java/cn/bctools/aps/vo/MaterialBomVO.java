package cn.bctools.aps.vo;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.math.BigDecimal;

/**
 * @author jvs
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@ApiModel("制造BOM物料详情")
public class MaterialBomVO {

    @ApiModelProperty(value = "bom id")
    private String id;

    @ApiModelProperty(value = "物料id")
    private String materialId;

    @ApiModelProperty(value = "物料编码")
    private String materialCode;

    @ApiModelProperty(value = "物料名")
    private String materialName;

    @ApiModelProperty(value = "计量单位")
    private String unit;

    @ApiModelProperty(value = "单件用量")
    private BigDecimal quantity;
}
