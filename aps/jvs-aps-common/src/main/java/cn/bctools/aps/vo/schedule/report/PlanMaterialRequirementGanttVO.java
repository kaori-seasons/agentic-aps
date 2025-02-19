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
@ApiModel("排产结果可视化——物料需求甘特图")
public class PlanMaterialRequirementGanttVO extends PlanScheduleReportVO {

    @ApiModelProperty("物料集合")
    private List<GanttMaterialVO> materials;

    @ApiModelProperty("物料需求集合")
    private List<GanttMaterialRequirementVO> requirements;
}
