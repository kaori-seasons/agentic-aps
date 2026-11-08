package cn.windwheel.aps.solve.model;

import cn.windwheel.aps.annotation.Comment;
import cn.windwheel.aps.entity.enums.ResourceStatusEnum;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.optaplanner.core.api.domain.entity.PlanningEntity;
import org.optaplanner.core.api.domain.lookup.PlanningId;
import org.optaplanner.core.api.domain.variable.PlanningListVariable;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@Accessors(chain = true)
@AllArgsConstructor
@NoArgsConstructor
@PlanningEntity
@ai.timefold.solver.core.api.domain.entity.PlanningEntity
public class MainProductionResource {
    @Comment("资源id")
    @PlanningId
    @ai.timefold.solver.core.api.domain.lookup.PlanningId
    private String id;

    @Comment("名称")
    private String name;
    @Comment("编码")
    private String code;
    @Comment("容量")
    private BigDecimal capacity;
    @Comment("容量计量单位")
    private String unit;
    @Comment("产能")
    private String throughput;
    @Comment("资源状态")
    private ResourceStatusEnum resourceStatus;
    @Comment("资源可用日历集合")
    List<WorkCalendar> workCalendars;

    @PlanningListVariable(valueRangeProviderRefs = "taskRanges")
    @ai.timefold.solver.core.api.domain.variable.PlanningListVariable(valueRangeProviderRefs = "timefoldTaskRanges")
    private List<ProductionTask> taskList = new ArrayList<>();

    /** Fixed before solving. A value's PlanningPin does not pin a planning list. */
    @ai.timefold.solver.core.api.domain.entity.PlanningPinToIndex
    private int pinnedTaskCount;

    @Override
    public String toString() {
        return "MainProductionResource{code='" + code + "'}";
    }
}
