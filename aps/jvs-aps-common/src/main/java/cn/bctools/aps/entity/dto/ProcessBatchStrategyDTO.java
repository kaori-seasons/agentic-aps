package cn.bctools.aps.entity.dto;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.experimental.Accessors;

import javax.validation.constraints.Min;

/**
 * @author jvs
 */
@Data
@Accessors(chain = true)
@ApiModel("工序——批量策略")
public class ProcessBatchStrategyDTO {
    @ApiModelProperty(value = "批次阈值")
    @Min(value = 0, message = "批次阈值不能小于0")
    private Integer batchThreshold;
}
