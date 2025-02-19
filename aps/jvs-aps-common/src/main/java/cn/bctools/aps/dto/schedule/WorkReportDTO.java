package cn.bctools.aps.dto.schedule;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * @author jvs
 */
@Data
@ApiModel("报工")
public class WorkReportDTO {
 
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

    @ApiModelProperty("计划结束时间")
    private LocalDateTime planEndTime;

    @ApiModelProperty("实际完成数量")
    private BigDecimal quantityCompleted;

    @ApiModelProperty("实际主资源")
    private String resourceCode;

    @ApiModelProperty("实际开始时间")
    private LocalDateTime startTime;

    @ApiModelProperty("实际完成时间")
    private LocalDateTime endTime;

    @ApiModelProperty("报工时间")
    private LocalDateTime reportTime;
}
