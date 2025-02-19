package cn.bctools.aps.dto.schedule.adjustment;

import cn.bctools.aps.dto.schedule.AdjustTaskMergeDTO;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.Size;
import java.util.List;

/**
 * @author jvs
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@ApiModel("合并——选中的任务")
public class MergeSelectedTaskDTO extends AdjustTaskMergeDTO {

    @ApiModelProperty(value = "待合并任务编码", required = true)
    @NotEmpty(message = "未选择待合并的任务")
    @Size(min = 2, message = "至少选择两个任务")
    private List<String> taskCodes;
}
