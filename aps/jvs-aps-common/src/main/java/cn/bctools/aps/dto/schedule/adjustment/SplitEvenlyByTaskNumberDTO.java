package cn.bctools.aps.dto.schedule.adjustment;

import cn.bctools.aps.dto.schedule.AdjustTaskSplitDTO;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;

/**
 * @author jvs
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@ApiModel("任务拆分——按任务数量平均拆分")
public class SplitEvenlyByTaskNumberDTO extends AdjustTaskSplitDTO {
    @ApiModelProperty(value = "任务编码", required = true)
    @NotBlank(message = "未选择任务")
    private String taskCode;

    @ApiModelProperty(value = "平均拆分为几个任务", required = true)
    @Min(value = 2, message = "最少拆为2个任务")
    private Integer splitTaskNumber;
}
