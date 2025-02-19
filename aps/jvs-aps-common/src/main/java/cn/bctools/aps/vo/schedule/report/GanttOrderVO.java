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
@ApiModel("甘特图中订单信息")
public class GanttOrderVO {

    @ApiModelProperty("订单id")
    private String id;

    @ApiModelProperty("订单号")
    private String code;
}
