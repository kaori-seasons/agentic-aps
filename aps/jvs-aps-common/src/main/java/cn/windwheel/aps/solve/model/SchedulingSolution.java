package cn.windwheel.aps.solve.model;

import cn.windwheel.aps.annotation.Comment;
import cn.windwheel.aps.entity.enums.SolverProfileEnum;
import cn.windwheel.common.utils.ObjectNull;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;
import lombok.experimental.Accessors;
import org.optaplanner.core.api.domain.solution.*;
import org.optaplanner.core.api.domain.valuerange.ValueRangeProvider;
import org.optaplanner.core.api.score.buildin.hardsoftlong.HardSoftLongScore;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** Shared business model. Each engine reads only its own annotations and score. */
@Data
@Accessors(chain = true)
@PlanningSolution
@ai.timefold.solver.core.api.domain.solution.PlanningSolution
public class SchedulingSolution {
    @Comment("排程计划执行时间")
    @ProblemFactProperty
    @ai.timefold.solver.core.api.domain.solution.ProblemFactProperty
    private LocalDateTime planTime;

    @Comment("排产策略")
    @ProblemFactProperty
    @ai.timefold.solver.core.api.domain.solution.ProblemFactProperty
    private PlanningStrategy strategy;

    @Comment("生产订单")
    @ProblemFactCollectionProperty
    @ai.timefold.solver.core.api.domain.solution.ProblemFactCollectionProperty
    private List<ProductionOrder> productionOrders;

    @Comment("主资源")
    @PlanningEntityCollectionProperty
    @ai.timefold.solver.core.api.domain.solution.PlanningEntityCollectionProperty
    private List<MainProductionResource> mainResources;

    @Comment("生产任务")
    @PlanningEntityCollectionProperty
    @ai.timefold.solver.core.api.domain.solution.PlanningEntityCollectionProperty
    private List<ProductionTask> tasks;

    @PlanningScore
    private HardSoftLongScore score;

    @JsonIgnore
    @com.alibaba.fastjson.annotation.JSONField(serialize = false, deserialize = false)
    @ai.timefold.solver.core.api.domain.solution.PlanningScore
    private ai.timefold.solver.core.api.score.buildin.hardsoftlong.HardSoftLongScore timefoldScore;

    /** Legacy range excludes locked tasks, retaining existing OptaPlanner behavior. */
    @ValueRangeProvider(id = "taskRanges")
    public List<ProductionTask> taskRanges() {
        Stream<ProductionTask> range = tasks.stream()
                .filter(task -> !Boolean.TRUE.equals(task.getPinned()))
                .filter(task -> ObjectNull.isNull(task.getMergeTaskCode()));
        SolverProfileEnum profile = strategy == null || strategy.getConfig() == null
                ? SolverProfileEnum.DEFAULT : strategy.getConfig().getSolverProfile();
        if (profile == SolverProfileEnum.FFD_TABU || profile == SolverProfileEnum.FFD_LATE_ACCEPTANCE) {
            range = range.sorted(new TaskDifficultyComparator().reversed());
        }
        return range.collect(Collectors.toList());
    }

    /** Timefold list pinning keeps locked values in the range and pins their list prefix. */
    @ai.timefold.solver.core.api.domain.valuerange.ValueRangeProvider(id = "timefoldTaskRanges")
    public List<ProductionTask> timefoldTaskRanges() {
        return tasks.stream().filter(task -> ObjectNull.isNull(task.getMergeTaskCode()))
                .collect(Collectors.toList());
    }
}
