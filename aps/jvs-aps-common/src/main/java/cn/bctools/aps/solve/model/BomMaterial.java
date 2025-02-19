package cn.bctools.aps.solve.model;

import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.math.BigDecimal;

/**
 * @author jvs
 * BOM物料
 */
@Data
public class BomMaterial {
    @ApiModelProperty(value = "物料id")
    private String materialId;

    @ApiModelProperty(value = "单件用量")
    private BigDecimal quantity;
}
