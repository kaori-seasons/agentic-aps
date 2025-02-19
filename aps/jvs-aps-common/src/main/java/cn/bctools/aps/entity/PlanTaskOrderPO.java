package cn.bctools.aps.entity;

import cn.bctools.aps.entity.dto.plan.PlanMaterialInfoDTO;
import cn.bctools.aps.entity.dto.plan.PlanOrderInfoDTO;
import cn.bctools.database.handler.Fastjson2TypeHandler;
import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.annotations.ApiModelProperty;
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
 * 排产计划冗余任务订单
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Accessors(chain = true)
@TableName(value = "jvs_plan_task_order", autoResultMap = true)
public class PlanTaskOrderPO implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.ASSIGN_UUID)
    private String id;

    /**
     * 生产订单id
     */
    @TableField("order_id")
    private String orderId;

    /**
     * 生产订单编码
     */
    @TableField("order_code")
    private String orderCode;

    @TableField("delay_time_string")
    @ApiModelProperty(value = "订单延期时长", notes = "任务计划完成时间在订单交期时间后。格式化后的字符串")
    private String delayTimeString;

    /**
     * 冗余生产订单信息
     */
    @TableField(value = "order_info", typeHandler = Fastjson2TypeHandler.class)
    private PlanOrderInfoDTO orderInfo;

    /**
     * 冗余生产订单主产物信息
     */
    @TableField(value = "order_material_info", typeHandler = Fastjson2TypeHandler.class)
    private PlanMaterialInfoDTO orderMaterialInfo;

    @TableField(fill = FieldFill.INSERT)
    @JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
}
