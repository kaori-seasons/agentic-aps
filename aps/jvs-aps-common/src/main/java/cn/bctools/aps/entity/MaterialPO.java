package cn.bctools.aps.entity;

import cn.bctools.aps.entity.enums.MaterialSourceEnum;
import cn.bctools.aps.entity.enums.MaterialTypeEnum;
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
 * 物料
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Accessors(chain = true)
@TableName("jvs_material")
public class MaterialPO extends BasalPo implements Serializable {

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
     * 类型
     */
    @TableField("type")
    private MaterialTypeEnum type;

    /**
     * 来源
     */
    @TableField("source")
    private MaterialSourceEnum source;

    /**
     * 库存
     */
    @TableField("quantity")
    private BigDecimal quantity;

    /**
     * 安全库存
     */
    @TableField("safety_stock")
    private BigDecimal safetyStock;

    /**
     * 计量单位
     */
    @TableField("unit")
    private String unit;

    /**
     * 提前期
     */
    @TableField("lead_time")
    private String leadTime;

    /**
     * 缓冲期
     */
    @TableField("buffer_time")
    private String bufferTime;

    /**
     * 是否删除 0未删除  1已删除
     */
    @TableField("del_flag")
    private Boolean delFlag;
}
