package cn.bctools.aps.entity;

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
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * @author jvs
 * 报工记录
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Accessors(chain = true)
@TableName(value = "jvs_work_report")
public class WorkReportPO extends BasalPo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.ASSIGN_UUID)
    private String id;

    /**
     * 报工时间
     */
    @TableField("report_time")
    private LocalDateTime reportTime;

    /**
     * 订单id
     */
    @TableField("order_id")
    private String orderId;

    /**
     * 订单编码
     */
    @TableField("order_code")
    private String orderCode;

    /**
     * 产品编码
     */
    @TableField("material_code")
    private String materialCode;

    /**
     * 工序编码
     */
    @TableField("process_code")
    private String processCode;

    /**
     * 计划主资源编码
     */
    @TableField("plan_resource_code")
    private String planResourceCode;

    /**
     * 计划主资源id
     */
    @TableField("plan_resource_id")
    private String planResourceId;

    /**
     * 实际完成数量
     */
    @TableField("quantity_completed")
    private BigDecimal quantityCompleted;

    /**
     * 实际主资源编码
     */
    @TableField("resource_code")
    private String resourceCode;

    /**
     * 实际主资源id
     */
    @TableField("resource_id")
    private String resourceId;

    /**
     * 计划开始时间
     */
    @TableField("plan_start_time")
    private LocalDateTime planStartTime;

    /**
     * 计划结束时间
     */
    @TableField("plan_end_time")
    private LocalDateTime planEndTime;

    /**
     * 实际开始时间
     */
    @TableField("start_time")
    private LocalDateTime startTime;

    /**
     * 实际完成时间
     */
    @TableField("end_time")
    private LocalDateTime endTime;

}
