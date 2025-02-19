package cn.bctools.aps.dto;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * @author jvs
 * 所有分页条件继承此类
 */
@Data
@Accessors(chain = true)
@ApiModel("分页基础入参")
public class PageBaseDTO {
    @ApiModelProperty(value = "当前页", required = true)
    private Integer current;

    @ApiModelProperty(value = "每页显示条数", required = true)
    private Integer size;
}
