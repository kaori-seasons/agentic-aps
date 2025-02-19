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
@ApiModel("排产结果可视化——资源任务甘特图")
public class PlanResourceTaskGanttDTO extends PlanScheduleReportDTO {

    @ApiModelProperty(value = "资源组")
    private String resourceGroup;
}
