package cn.bctools.aps.dto.schedule.adjustment;

import cn.bctools.aps.dto.schedule.AdjustTaskFreezeDTO;
import io.swagger.annotations.ApiModel;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

/**
 * @author jvs
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@ApiModel("锁定|解锁——所有已开工的任务")
public class FreezeStartedTaskDTO extends AdjustTaskFreezeDTO {
}
