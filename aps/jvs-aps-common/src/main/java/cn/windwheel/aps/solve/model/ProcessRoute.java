package cn.windwheel.aps.solve.model;

import cn.windwheel.aps.annotation.Comment;
import cn.windwheel.aps.graph.Graph;
import cn.windwheel.aps.entity.dto.ProcessRouteNodePropertiesDTO;
import lombok.Data;

/**
 * @author jvs
 * 工艺路线
 */
@Data
public class ProcessRoute {
    @Comment("物料id")
    private String materialId;

    @Comment("工艺路线设计")
    private Graph<ProcessRouteNodePropertiesDTO> routeDesign;
}
