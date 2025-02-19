package cn.bctools.aps.vo.schedule;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * @author jvs
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@ApiModel("排产任务锁定/解锁结果")
public class TaskAdjustFreezeVO {
    @ApiModelProperty(value = "锁定解锁的任务")
    private List<GanttTaskFreezeVO> taskFreezes;
}
