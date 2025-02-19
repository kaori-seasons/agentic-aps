package cn.bctools.aps.dto.schedule.report;

import cn.bctools.aps.dto.schedule.PlanScheduleReportDTO;
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
@ApiModel("排产结果可视化——工作任务表")
public class PlanTaskReportDTO extends PlanScheduleReportDTO {

    @ApiModelProperty("订单id")
    private String orderId;

    @ApiModelProperty("任务编码")
    private String taskCode;
}
