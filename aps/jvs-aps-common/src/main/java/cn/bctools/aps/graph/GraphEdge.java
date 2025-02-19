package cn.bctools.aps.graph;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.experimental.Accessors;

import javax.validation.constraints.NotBlank;

/**
 * @author jvs
 */
@Data
@Accessors(chain = true)
@ApiModel("线")
public class GraphEdge {
    @ApiModelProperty("起始节点id")
    @NotBlank(message = "起始节点id不能为空")
    private String source;

    @ApiModelProperty("结束节点id")
    @NotBlank(message = "结束节点id不能为空")
    private String target;
}
