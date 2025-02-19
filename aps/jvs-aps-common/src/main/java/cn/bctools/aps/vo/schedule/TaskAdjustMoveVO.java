package cn.bctools.aps.vo.schedule;

import cn.bctools.aps.vo.schedule.report.ResourceGanttTaskVO;
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
@ApiModel("排产任务移动结果")
public class TaskAdjustMoveVO {
    @ApiModelProperty(value = "任务移动后需要刷新的任务")
    private List<ResourceGanttTaskVO> refreshPartialTasks;
}
