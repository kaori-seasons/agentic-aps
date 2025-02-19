package cn.bctools.aps.dto;

import cn.bctools.aps.enums.DayOfWeekEnum;
import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.experimental.Accessors;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * @author jvs
 */
@Data
@Accessors(chain = true)
@ApiModel("新增工作日历")
public class SaveWorkCalendarDTO {
    @ApiModelProperty(value = "名称", required = true)
    @NotBlank(message = "名称不能为空")
    private String name;

    @ApiModelProperty(value = "工作模式id")
    @NotBlank(message = "工作模式不能为空")
    private String workModeId;

    @ApiModelProperty(value = "起始时间")
    @JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime beginTime;

    @ApiModelProperty(value = "截止时间")
    @JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime endTime;

    @ApiModelProperty(value = "是否启用 false-未启用，true-启用", required = true)
    @NotNull(message = "未设置是否启用")
    private Boolean enabled;

    @ApiModelProperty(value = "优先级。日历生效时间冲突时，需要设置不同的优先级", required = true)
    @NotNull(message = "优先级不能为空")
    private Integer priority;

    @ApiModelProperty(value = "工作日设置", required = true)
    @NotNull(message = "工作日设置不能为空")
    private Map<DayOfWeekEnum, Boolean> workDaySetting;

    @ApiModelProperty(value = "使用此日历的资源id集合")
    private List<String> resourceIds;
}
