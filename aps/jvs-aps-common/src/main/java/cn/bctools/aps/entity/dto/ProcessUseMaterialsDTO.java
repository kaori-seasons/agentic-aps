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
@ApiModel("工序——使用的物料")
public class ProcessUseMaterialsDTO {
    @ApiModelProperty(value = "物料", required = true)
    @NotBlank(message = "未选择物料")
    private String id;

    @ApiModelProperty(value = "物料数量", required = true)
    @NotNull(message = "物料数量不能为空")
    @DecimalPositive(message = "物料数量必须是正数")
    @DecimalPlacesMax(value = 6, message = "物料数量最多6位小数")
    private BigDecimal quantity;

    @ApiModelProperty(value = "true-使用, false-不使用", required = true)
    @NotNull(message = "未设置是否使用物料")
    private Boolean use;
}
