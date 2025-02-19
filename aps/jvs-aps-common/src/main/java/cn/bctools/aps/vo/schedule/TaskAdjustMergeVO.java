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
@ApiModel("排产任务合并结果")
public class TaskAdjustMergeVO {
    @ApiModelProperty(value = "任务合并后需要刷新的任务")
    private List<ResourceGanttTaskVO> refreshPartialTasks;

    @ApiModelProperty("任何合并后，需要从甘特图上移除的任务编码集合")
    private List<String> removeTaskCodes;
}
