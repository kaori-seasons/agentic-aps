package cn.bctools.aps.dto;

import cn.bctools.aps.annotation.DecimalPlacesMax;
import cn.bctools.aps.annotation.DurationFormat;
import cn.bctools.aps.entity.enums.MaterialSourceEnum;
import cn.bctools.aps.entity.enums.MaterialTypeEnum;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.experimental.Accessors;
import org.hibernate.validator.constraints.Length;

import javax.validation.constraints.*;
import java.math.BigDecimal;

/**
 * @author jvs
 */
@Data
@Accessors(chain = true)
@ApiModel("新增物料")
public class SaveMaterialDTO {
    @ApiModelProperty(value = "名称", required = true)
    @NotBlank(message = "物料名称不能为空")
    private String name;

    @ApiModelProperty(value = "编码", required = true)
    @NotBlank(message = "物料编码不能为空")
    @Length(max = 30, message = "编码最多30个字符")
    private String code;

    @ApiModelProperty(value = "类型", required = true)
    @NotNull(message = "物料类型不能为空")
    private MaterialTypeEnum type;

    @ApiModelProperty(value = "来源", required = true)
    @NotNull(message = "物料来源不能为空")
    private MaterialSourceEnum source;

    @ApiModelProperty(value = "库存", required = true)
    @DecimalMin(value = "0", message = "库存不能小于0")
    @DecimalPlacesMax(value = 6, message = "库存最多6位小数")
    private BigDecimal quantity;

    @ApiModelProperty("安全库存")
    @DecimalMin(value = "0", message = "安全库存不能小于0")
    @DecimalPlacesMax(value = 6, message = "安全库存最多6位小数")
    private BigDecimal safetyStock;

    @ApiModelProperty(value = "计量单位", required = true)
    @NotBlank(message = "计量单位不能为空")
    private String unit;

    @ApiModelProperty(value = "提前期。 格式为：数字 + 日期单位。 如：5D 表示5天; 4H 表示4小时")
    @DurationFormat(message = "提前期格式错误")
    private String leadTime;

    @ApiModelProperty(value = "缓冲期。 格式为：数字 + 日期单位。 如：5D 表示5天; 4H 表示4小时")
    @DurationFormat(message = "缓冲期格式错误")
    private String bufferTime;
}
