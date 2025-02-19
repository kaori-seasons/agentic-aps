package cn.bctools.aps.dto.schedule.adjustment;

import cn.bctools.aps.dto.schedule.AdjustTaskSplitDTO;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import javax.validation.constraints.NotBlank;

/**
 * @author jvs
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@ApiModel("任务拆分——按完成数量拆分")
public class SplitByCompletionDTO extends AdjustTaskSplitDTO {
    @ApiModelProperty(value = "任务编码", required = true)
    @NotBlank(message = "未选择任务")
    private String taskCode;
}
