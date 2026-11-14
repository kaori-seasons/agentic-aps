package cn.windwheel.aps.solve.model;

import cn.windwheel.aps.annotation.Comment;
import cn.windwheel.common.utils.ObjectNull;
import lombok.Data;
import lombok.experimental.Accessors;
import org.optaplanner.core.api.domain.solution.*;
import org.optaplanner.core.api.domain.valuerange.ValueRangeProvider;
import org.optaplanner.core.api.score.buildin.hardsoftlong.HardSoftLongScore;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @author jvs
 * 规划解决方案
 */
@Data
@Accessors(chain = true)
@ai.timefold.solver.core.api.domain.solution.PlanningSolution
@PlanningSolution
public class SchedulingSolution {

    @Comment("排程计划执行时间")
    @ai.timefold.solver.core.api.domain.solution.ProblemFactProperty
    @ProblemFactProperty
    private LocalDateTime planTime;

    @Comment("排产策略")
    @ai.timefold.solver.core.api.domain.solution.ProblemFactProperty
    @ProblemFactProperty
    private PlanningStrategy strategy;

    @Comment("生产订单")
    @ai.timefold.solver.core.api.domain.solution.ProblemFactCollectionProperty
    @ProblemFactCollectionProperty
    private List<ProductionOrder> productionOrders;

    @Comment("主资源")
    @ai.timefold.solver.core.api.domain.solution.PlanningEntityCollectionProperty
    @PlanningEntityCollectionProperty
    private List<MainProductionResource> mainResources;

    @Comment("生产任务")
    @ProblemFactCollectionProperty
    @ValueRangeProvider
    @ai.timefold.solver.core.api.domain.solution.PlanningEntityCollectionProperty
    private List<ProductionTask> tasks;

    @ai.timefold.solver.core.api.domain.solution.PlanningScore
    private ai.timefold.solver.core.api.score.buildin.hardsoftlong.HardSoftLongScore timefoldScore;

    @ai.timefold.solver.core.api.domain.valuerange.ValueRangeProvider(id = "taskRanges")
    public List<ProductionTask> timefoldTaskRanges() {
        return tasks.stream().filter(task -> ObjectNull.isNull(task.getMergeTaskCode()))
                .collect(Collectors.toList());
    }

    @PlanningScore
    private HardSoftLongScore score;

    /**
     * 指定可选择的任务范围
     */
    @ValueRangeProvider(id = "taskRanges")
    public List<ProductionTask> taskRanges() {
        return tasks.stream()
                // 未锁定的任务可重排
                .filter(job -> !Boolean.TRUE.equals(job.getPinned()))
                // 非合并任务的子任务可重排
                .filter(job -> ObjectNull.isNull(job.getMergeTaskCode()))
                .collect(Collectors.toList());
    }
}
