package cn.bctools.aps.solve.model;

import cn.bctools.aps.annotation.Comment;
import cn.bctools.aps.graph.Graph;
import cn.bctools.aps.entity.dto.ProcessRouteNodePropertiesDTO;
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
