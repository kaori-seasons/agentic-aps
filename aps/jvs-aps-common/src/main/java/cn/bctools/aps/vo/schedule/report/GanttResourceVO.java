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
@ApiModel("甘特图中资源信息")
public class GanttResourceVO {

    @ApiModelProperty("资源id")
    private String id;

    @ApiModelProperty("资源编码")
    private String code;

    @ApiModelProperty("资源名称")
    private String name;

}
