package cn.bctools.aps.vo.schedule;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * @author jvs
 */
@Data
@Accessors(chain = true)
@ApiModel("派工")
public class WorkAssignmentVO {
    @ApiModelProperty("订单")
    private String orderCode;

    @ApiModelProperty("产品")
    private String materialCode;

    @ApiModelProperty("工序")
    private String processCode;

    @ApiModelProperty("计划主资源")
    private String planResourceCode;

    @ApiModelProperty("计划开始时间")
    private LocalDateTime planStartTime;

    @ApiModelProperty("计划完成时间")
    private LocalDateTime planEndTime;

    @ApiModelProperty("计划工时")
    private String planWorkHours;

    @ApiModelProperty("总计划数")
    private BigDecimal scheduledQuantity;

    @ApiModelProperty("计划数量")
    private BigDecimal planQuantity;
}
