package cn.bctools.aps.vo;

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
@ApiModel("工作模式详情")
public class DetailWorkModeVO extends BaseVO {
    @ApiModelProperty(value = "模式名称")
    private String name;

    @ApiModelProperty(value = "模式")
    private String workingMode;
}
