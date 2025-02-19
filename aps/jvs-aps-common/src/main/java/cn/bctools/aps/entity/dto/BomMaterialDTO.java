package cn.bctools.aps.entity.dto;

import cn.bctools.aps.annotation.DecimalPlacesMax;
import cn.bctools.aps.annotation.DecimalPositive;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.experimental.Accessors;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.math.BigDecimal;

/**
 * @author jvs
 */
@Data
@Accessors(chain = true)
@ApiModel("BOM物料")
public class BomMaterialDTO {
    @ApiModelProperty(value = "物料id", required = true)
    @NotBlank(message = "子件物料不能为空")
    private String materialId;

    @ApiModelProperty(value = "单件用量", required = true)
    @NotNull(message = "单件用量不能为空")
    @DecimalPositive(message = "单件用量必须是正数")
    @DecimalPlacesMax(value = 6, message = "物料数量最多6位小数")
    private BigDecimal quantity;
}
