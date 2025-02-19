package cn.bctools.aps.solve.model;

import cn.bctools.aps.annotation.Comment;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalTime;

/**
 * @author jvs
 * 工作模式起止时间
 */
@Data
@Accessors(chain = true)
public class WorkModeTime {
    @Comment("开始时间")
    private LocalTime start;

    @Comment("结束时间")
    private LocalTime end;
}
