package cn.bctools.aps.solve.model;

import cn.bctools.aps.annotation.Comment;
import cn.bctools.aps.entity.PlanTaskAdjustPO;
import cn.bctools.aps.entity.dto.ProcessRouteNodePropertiesDTO;
import cn.bctools.aps.graph.Graph;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;
import java.util.Map;

/**
 * @author jvs
 * 基础数据
 */
@Data
@Accessors(chain = true)
public class BasicData {
    @Comment(value = "物料", notes = "Map<物料id, 物料信息>")
    private Map<String, Material> materialMap;

    @Comment(value = "工艺路线", notes = "Map<物料id, 工艺路线>")
    private Map<String, Graph<ProcessRouteNodePropertiesDTO>> processRouteMap;

    @Comment(value = "日历", notes = "Map<日历id, 日历>")
    private Map<String, WorkCalendar> workCalendarMap;

    @Comment("资源")
    private List<ProductionResource> productionResources;

    @Comment(value = "制造BOM", notes = "Map<物料id, 子件物料集合>")
    private Map<String, List<BomMaterial>> bomMap;

    @Comment(value = "来料订单", notes = "Map<物料id, 来料订单集合>")
    private Map<String, List<IncomingMaterialOrder>> incomingMaterialOrderMap;

    @Comment("生产订单")
    private List<ProductionOrder> productionOrders;

    @Comment("已锁定的生产任务")
    private List<PlanTaskAdjustPO> pinnedTasks;
}
