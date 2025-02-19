package cn.bctools.aps.solve.model;

import cn.bctools.aps.annotation.Comment;
import cn.bctools.aps.enums.DayOfWeekEnum;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * @author jvs
 * 工作日历
 */
@Data
@Accessors(chain = true)
public class WorkCalendar {
    @Comment("日历id")
    private String id;

    @Comment("日历起始时间")
    private LocalDateTime beginTime;

    @Comment("日历截止时间")
    private LocalDateTime endTime;

    @Comment(value = "工作模式起止时间", notes = "以开始时间顺序排序的有序集合")
    private List<WorkModeTime> workModeTimes;

    @Comment("工作日设置")
    private Map<DayOfWeekEnum, Boolean> workDaySetting;

    @Comment("优先级")
    private Integer priority;
}
