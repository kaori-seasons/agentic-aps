package cn.bctools.aps.graph;

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
@ApiModel("点")
public class GraphNode<T> {
    @ApiModelProperty(value = "节点id", required = true)
    @NotBlank(message = "节点id不能为空")
    private String id;

    @ApiModelProperty(value = "节点数据")
    @Valid
    private T data;

    @ApiModelProperty(value = "样式数据")
    private Object style;
}
