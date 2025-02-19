package cn.bctools.aps.entity;

import cn.bctools.database.entity.po.BasalPo;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * @author jvs
 * 工作日历
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Accessors(chain = true)
@TableName("jvs_work_calendar")
public class WorkCalendarPO extends BasalPo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.ASSIGN_UUID)
    private String id;

    /**
     * 名称
     */
    @TableField("name")
    private String name;

    /**
     * 工作模式id
     */
    @TableField("work_mode_id")
    private String workModeId;

    /**
     * 起始时间
     */
    @TableField("begin_time")
    @JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime beginTime;

    /**
     * 截止时间
     */
    @TableField("end_time")
    @JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime endTime;

    /**
     * 是否启用 false-未启用，true-启用
     */
    @TableField("enabled")
    private Boolean enabled;

    /**
     * 工作日设置
     */
    @TableField("work_days")
    private byte[] workDays;

    /**
     * 优先级
     */
    @TableField("priority")
    private Integer priority;

    /**
     * 是否删除 0未删除  1已删除
     */
    @TableField("del_flag")
    private Boolean delFlag;
}
