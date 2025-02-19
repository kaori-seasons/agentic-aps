package cn.bctools.aps.dto;

import cn.bctools.aps.entity.enums.MaterialSourceEnum;
import cn.bctools.aps.entity.enums.MaterialTypeEnum;
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
@ApiModel("分页查询物料")
public class PageMaterialDTO extends PageBaseDTO {
    @ApiModelProperty(value = "名称")
    private String name;

    @ApiModelProperty(value = "编码")
    private String code;

    @ApiModelProperty(value = "关键字查询")
    private String keywords;

    @ApiModelProperty(value = "类型")
    private MaterialTypeEnum type;

    @ApiModelProperty(value = "来源")
    private MaterialSourceEnum source;
}
