package cn.bctools.aps.dto.schedule.adjustment;

import cn.bctools.aps.dto.schedule.AdjustTaskFreezeDTO;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import javax.validation.constraints.NotEmpty;
import java.util.Set;

/**
 * @author jvs
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@ApiModel("锁定|解锁——选中的任务")
public class FreezeSelectedTaskDTO extends AdjustTaskFreezeDTO {
    @ApiModelProperty(value = "任务编码集合", required = true)
    @NotEmpty(message = "未选择任务")
    private Set<String> taskCodes;
}
