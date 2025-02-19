package cn.bctools.aps.entity;

import cn.bctools.aps.entity.enums.ResourceStatusEnum;
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

/**
 * @author jvs
 * 资源
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Accessors(chain = true)
@TableName("jvs_production_resource")
public class ProductionResourcePO extends BasalPo implements Serializable {

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
     * 编码
     */
    @TableField("code")
    private String code;

    /**
     * 资源组
     */
    @TableField("resource_group")
    private String resourceGroup;

    /**
     * 容量
     */
    @TableField("capacity")
    private BigDecimal capacity;

    /**
     * 容量计量单位
     */
    @TableField("unit")
    private String unit;

    /**
     * 产能
     */
    @TableField("throughput")
    private String throughput;

    /**
     * 资源状态
     */
    @TableField("resource_status")
    private ResourceStatusEnum resourceStatus;

    /**
     * 是否删除 0未删除  1已删除
     */
    @TableField("del_flag")
    private Boolean delFlag;
}
