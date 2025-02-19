package cn.bctools.aps.dto.schedule;

import cn.bctools.aps.annotation.DecimalPlacesMax;
import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import javax.validation.constraints.DecimalMin;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * @author jvs
 */
@Data
@ApiModel("修改任务报工")
public class UpdateWorkReportProgress {
    @ApiModelProperty(value = "任务编码", required = true)
    @NotBlank(message = "未选择任务")
    private String taskCode;

    @ApiModelProperty(value = "报工数量", required = true)
    @NotNull(message = "数量不能为空")
    @DecimalMin(value = "0", message = "数量不能小于0")
    @DecimalPlacesMax(value = 6, message = "数量最多6位小数")
    private BigDecimal quantityCompleted;

    @ApiModelProperty(value = "实际完成时间", required = true)
    @NotNull(message = "实际完成时间不能为空")
    @JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime endTime;
}
