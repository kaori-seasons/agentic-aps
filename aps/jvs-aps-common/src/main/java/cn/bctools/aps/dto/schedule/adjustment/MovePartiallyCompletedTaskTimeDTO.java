package cn.bctools.aps.dto.schedule.adjustment;

import cn.bctools.aps.dto.schedule.AdjustTaskMoveDTO;
import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import javax.validation.constraints.NotNull;
import java.time.LocalDateTime;

/**
 * @author jvs
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@ApiModel("移动部分完成的任务进度到指定时间")
public class MovePartiallyCompletedTaskTimeDTO extends AdjustTaskMoveDTO {
    @ApiModelProperty(value = "时间", required = true)
    @NotNull(message = "未指定时间")
    @JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime time;
}
