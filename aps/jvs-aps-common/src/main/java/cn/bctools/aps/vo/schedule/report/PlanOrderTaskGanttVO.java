package cn.bctools.aps.vo.schedule.report;

import cn.bctools.aps.vo.schedule.PlanScheduleReportVO;
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
@ApiModel("排产结果可视化——订单任务甘特图")
public class PlanOrderTaskGanttVO extends PlanScheduleReportVO {

    @ApiModelProperty("订单集合")
    private List<GanttOrderVO> orders;

    @ApiModelProperty("任务集合")
    private List<OrderGanttTaskVO> tasks;
}
