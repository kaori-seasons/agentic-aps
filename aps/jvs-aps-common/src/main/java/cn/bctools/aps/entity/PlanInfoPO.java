package cn.bctools.aps.entity;

import cn.bctools.aps.entity.enums.PlanInfoStatusEnum;
import cn.bctools.database.entity.po.BasalPo;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
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
 * 排产计划基本信息
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Accessors(chain = true)
@TableName("jvs_plan_info")
public class PlanInfoPO extends BasalPo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.ASSIGN_UUID)
    private String id;

    /**
     * 计划开始时间(排产策略中的开始时间)
     */
    @TableField("schedule_start_time")
    private LocalDateTime scheduleStartTime;

    /**
     * 任务最早开始时间
     */
    @TableField("earliest_task_start_time")
    private LocalDateTime earliestTaskStartTime;

    /**
     * 最近任务派工截止时间
     */
    @TableField("last_task_assignment_time")
    private LocalDateTime lastTaskAssignmentTime;

    /**
     * 计划状态
     */
    @TableField("plan_status")
    private PlanInfoStatusEnum planStatus;

    /**
     * 租户id
     */
    @TableField("tenant_id")
    private String tenantId;
}
