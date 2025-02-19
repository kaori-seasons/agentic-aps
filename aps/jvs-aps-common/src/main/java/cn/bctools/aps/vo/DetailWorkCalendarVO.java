package cn.bctools.aps.vo;

import cn.bctools.aps.enums.DayOfWeekEnum;
import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * @author jvs
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@ApiModel("工作日历详情")
public class DetailWorkCalendarVO extends BaseVO {
    @ApiModelProperty(value = "名称")
    private String name;

    @ApiModelProperty(value = "工作模式id")
    private String workModeId;

    @ApiModelProperty(value = "工作模式名称")
    private String workModeName;

    @ApiModelProperty(value = "起始时间")
    @JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime beginTime;

    @ApiModelProperty(value = "截止时间")
    @JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime endTime;

    @ApiModelProperty(value = "是否启用 false-不启用，true-启用")
    private Boolean enabled;

    @ApiModelProperty(value = "优先级")
    private Integer priority;

    @ApiModelProperty(value = "工作日设置")
    private Map<DayOfWeekEnum, Boolean> workDaySetting;
}
