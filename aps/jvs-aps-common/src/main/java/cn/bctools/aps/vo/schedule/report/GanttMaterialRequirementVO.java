package cn.bctools.aps.vo.schedule.report;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

/**
 * @author jvs
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@ApiModel("物料需求甘特图——物料需求")
public class GanttMaterialRequirementVO {
    @ApiModelProperty("物料id")
    private String id;

    @ApiModelProperty(value = "物料总数量")
    private BigDecimal totalQuantity;

    @ApiModelProperty("每日需求，日期->数量")
    private Map<LocalDate, BigDecimal> dailyDemand;

}
