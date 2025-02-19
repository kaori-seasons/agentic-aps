package cn.bctools.aps.vo;

import cn.bctools.aps.entity.enums.MaterialSourceEnum;
import cn.bctools.aps.entity.enums.MaterialTypeEnum;
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
@ApiModel("物料详情")
public class DetailMaterialVO extends BaseVO {
    @ApiModelProperty(value = "名称")
    private String name;

    @ApiModelProperty(value = "编码")
    private String code;

    @ApiModelProperty(value = "类型")
    private MaterialTypeEnum type;

    @ApiModelProperty(value = "来源")
    private MaterialSourceEnum source;

    @ApiModelProperty(value = "库存")
    private BigDecimal quantity;

    @ApiModelProperty("安全库存")
    private BigDecimal safetyStock;

    @ApiModelProperty(value = "计量单位")
    private String unit;

    @ApiModelProperty(value = "提前期", notes = "格式为：数字 + 日期单位。 如：5D 表示5天; 4H 表示4小时")
    private String leadTime;

    @ApiModelProperty(value = "缓冲期", notes = "格式为：数字 + 日期单位。 如：5D 表示5天; 4H 表示4小时")
    private String bufferTime;
}
