package cn.bctools.aps.entity;

import cn.bctools.aps.entity.dto.BomMaterialDTO;
import cn.bctools.aps.entity.handler.ChildMaterialTypeHandler;
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
import java.util.List;

/**
 * @author jvs
 * 制造BOM
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Accessors(chain = true)
@TableName(value = "jvs_manufacture_bom", autoResultMap = true)
public class ManufactureBomPO extends BasalPo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.ASSIGN_UUID)
    private String id;

    /**
     * 物料id
     */
    @TableField("material_id")
    private String materialId;

    /**
     * 子件物料
     */
    @TableField(value = "child_materials", typeHandler = ChildMaterialTypeHandler.class)
    private List<BomMaterialDTO> childMaterials;

    /**
     * 是否删除 0未删除  1已删除
     */
    @TableField("del_flag")
    private Boolean delFlag;

}
