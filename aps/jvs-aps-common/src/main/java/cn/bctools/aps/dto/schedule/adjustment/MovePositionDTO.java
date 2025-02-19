package cn.bctools.aps.dto.schedule.adjustment;

import cn.bctools.aps.dto.schedule.AdjustTaskMoveDTO;
import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.time.LocalDateTime;

/**
 * @author jvs
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@ApiModel("移动指定任务")
public class MovePositionDTO extends AdjustTaskMoveDTO {

    @ApiModelProperty(value = "任务编码", required = true)
    @NotBlank(message = "未选择任务")
    private String taskCode;

    @ApiModelProperty(value = "目标资源", required = true)
    @NotBlank(message = "目标资源不能为空")
    private String resourceId;

    @ApiModelProperty(value = "新的计划开始时间", required = true)
    @NotNull(message = "未指定计划开始时间")
    @JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime newStartTime;
}
