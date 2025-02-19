package cn.bctools.aps.vo.schedule.report;

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
@ApiModel("甘特图中物料信息")
public class GanttMaterialVO {

    @ApiModelProperty("物料id")
    private String id;

    @ApiModelProperty("物料编码")
    private String code;

    @ApiModelProperty("物料名")
    private String name;
}
