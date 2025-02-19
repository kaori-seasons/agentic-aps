package cn.bctools.aps.vo.schedule;

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
@ApiModel("甘特图中任务锁定状态信息")
public class GanttTaskFreezeVO {
    @ApiModelProperty("任务编码")
    private String code;

    @ApiModelProperty("是否锁定任务")
    private Boolean pinned;
}
