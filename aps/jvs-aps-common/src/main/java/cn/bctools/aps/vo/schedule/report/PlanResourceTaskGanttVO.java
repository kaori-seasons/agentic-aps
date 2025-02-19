package cn.bctools.aps.vo.schedule.report;

import cn.bctools.aps.vo.schedule.PlanScheduleReportVO;
import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;
import java.util.List;

/**
 * @author jvs
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@ApiModel("排产结果可视化——资源任务甘特图")
public class PlanResourceTaskGanttVO extends PlanScheduleReportVO {

    @ApiModelProperty("任务计划最早开始时间")
    @JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime earliestTaskStartTime;

    @ApiModelProperty("最近任务派工截止时间")
    @JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime lastTaskAssignmentTime;

    @ApiModelProperty("资源集合")
    private List<GanttResourceVO> resources;

    @ApiModelProperty("任务集合")
    private List<ResourceGanttTaskVO> tasks;
}
