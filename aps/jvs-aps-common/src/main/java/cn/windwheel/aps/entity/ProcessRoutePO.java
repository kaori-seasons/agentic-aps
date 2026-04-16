package cn.windwheel.aps.entity;

import cn.windwheel.aps.entity.dto.ProcessRouteNodePropertiesDTO;
import cn.windwheel.aps.entity.handler.ProcessRouteDesignTypeHandler;
import cn.windwheel.aps.graph.Graph;
import cn.windwheel.database.entity.po.BasalPo;
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

/**
 * @author jvs
 * 工艺路线
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Accessors(chain = true)
@TableName(value = "jvs_process_route", autoResultMap = true)
public class ProcessRoutePO extends BasalPo implements Serializable {

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
     * 工艺路线设计
     */
    @TableField(value = "route_design", typeHandler = ProcessRouteDesignTypeHandler.class)
    private Graph<ProcessRouteNodePropertiesDTO> routeDesign;

    /**
     * 是否启用 false-不启用，true-启用
     */
    @TableField("enabled")
    private Boolean enabled;

    /**
     * 是否删除 0未删除  1已删除
     */
    @TableField("del_flag")
    private Boolean delFlag;
}
