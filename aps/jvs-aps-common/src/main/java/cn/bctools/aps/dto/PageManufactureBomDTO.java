package cn.bctools.aps.dto;

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
@ApiModel("分页查询有BOM的物料")
public class PageManufactureBomDTO extends PageBaseDTO {
    @ApiModelProperty(value = "关键字查询")
    private String keywords;
}
