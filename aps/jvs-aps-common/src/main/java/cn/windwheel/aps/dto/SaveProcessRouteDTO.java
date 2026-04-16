package cn.windwheel.aps.dto;

import cn.windwheel.aps.graph.Graph;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.experimental.Accessors;

import javax.validation.Valid;
import javax.validation.constraints.NotBlank;

/**
 * @author jvs
 */
@Data
@Accessors(chain = true)
@ApiModel("新增工艺路线")
public class SaveProcessRouteDTO {
    @ApiModelProperty(value = "物料id", required = true)
    @NotBlank(message = "未选择物料")
    private String materialId;

    @ApiModelProperty(value = "工艺路线设计")
    @Valid
    private Graph<SaveProcessDTO> routeDesign;
}
