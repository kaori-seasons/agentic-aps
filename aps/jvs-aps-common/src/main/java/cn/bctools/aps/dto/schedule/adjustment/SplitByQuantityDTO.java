package cn.bctools.aps.dto.schedule.adjustment;

import cn.bctools.aps.dto.schedule.AdjustTaskSplitDTO;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import javax.validation.constraints.DecimalMin;
import javax.validation.constraints.NotBlank;
import java.math.BigDecimal;

/**
 * @author jvs
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@ApiModel("任务拆分——按拆出数量拆分")
public class SplitByQuantityDTO extends AdjustTaskSplitDTO {
    @ApiModelProperty(value = "任务编码", required = true)
    @NotBlank(message = "未选择任务")
    private String taskCode;

    @ApiModelProperty(value = "拆出数量", required = true)
    @DecimalMin(value = "1", message = "拆出数量必须大于0")
    private BigDecimal splitQuantity;
}
