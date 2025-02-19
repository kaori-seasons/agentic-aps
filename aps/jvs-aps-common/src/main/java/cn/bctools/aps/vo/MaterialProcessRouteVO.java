package cn.bctools.aps.vo;

import cn.bctools.aps.graph.Graph;
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
@ApiModel("物料工艺路线")
public class MaterialProcessRouteVO extends BaseVO {

    @ApiModelProperty(value = "物料id")
    private String materialId;

    @ApiModelProperty(value = "工艺路线")
    private Graph<DetailProcessVO> routeDesign;

    @ApiModelProperty(value = "false-未启用，true-启用")
    private Boolean enabled;
}
