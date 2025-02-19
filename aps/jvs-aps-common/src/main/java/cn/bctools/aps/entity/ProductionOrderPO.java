package cn.bctools.aps.entity;

import cn.bctools.aps.annotation.Comment;
import cn.bctools.aps.entity.enums.*;
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
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * @author jvs
 * 生产订单
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Accessors(chain = true)
@TableName("jvs_production_order")
public class ProductionOrderPO extends BasalPo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Comment(value = "主键id")
    @TableId(value = "id", type = IdType.ASSIGN_UUID)
    private String id;

    @Comment(value = "订单号")
    @TableField("code")
    private String code;

    @Comment(value = "物料编码")
    @TableField("material_code")
    private String materialCode;

    @Comment(value = "需求数量")
    @TableField("quantity")
    private BigDecimal quantity;

    @Comment(value = "需求交付时间")
    @TableField("delivery_time")
    @JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime deliveryTime;

    @Comment(value = "优先级")
    @TableField("priority")
    private Integer priority;

    @Comment(value = "序号")
    @TableField("sequence")
    private BigDecimal sequence;

    @Comment(value = "订单类型")
    @TableField("type")
    private OrderTypeEnum type;

    @Comment(value = "订单状态")
    @TableField("order_status")
    private OrderStatusEnum orderStatus;

    @Comment(value = "排产状态")
    @TableField("scheduling_status")
    private OrderSchedulingStatusEnum schedulingStatus;

    @Comment(value = "显示颜色")
    @TableField("color")
    private String color;

    @Comment(value = "是否参与排产", notes = "false-否，true-是")
    @TableField("can_schedule")
    private Boolean canSchedule;

    @Comment(value = "是否为补充生产订单", notes = "false-否, true-是")
    @TableField("supplement")
    private Boolean supplement;

    @Comment(value = "父订单编码（如果是补充订单，则指向其是那个订单的补充订单")
    @TableField("parent_order_code")
    private String parentOrderCode;

    @Comment(value = "是否删除", notes = "false-未删除, true-已删除")
    @TableField("del_flag")
    private Boolean delFlag;
}
