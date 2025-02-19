package cn.bctools.aps.solve.model;

import cn.bctools.aps.annotation.Comment;
import cn.bctools.common.utils.ObjectNull;
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
@PlanningSolution
public class SchedulingSolution {

    @Comment("排程计划执行时间")
    @ProblemFactProperty
    private LocalDateTime planTime;

    @Comment("排产策略")
    @ProblemFactProperty
    private PlanningStrategy strategy;

    @Comment("生产订单")
    @ProblemFactCollectionProperty
    private List<ProductionOrder> productionOrders;

    @Comment("主资源")
    @PlanningEntityCollectionProperty
    private List<MainProductionResource> mainResources;

    @Comment("生产任务")
    @ProblemFactCollectionProperty
    @ValueRangeProvider
    private List<ProductionTask> tasks;

    @PlanningScore
    private HardSoftLongScore score;

    /**
     * 指定可选择的任务范围
     */
    @ValueRangeProvider(id = "taskRanges")
    public List<ProductionTask> taskRanges() {
        return tasks.stream()
                // 未锁定的任务可重排
                .filter(job -> !job.getPinned())
                // 非合并任务的子任务可重排
                .filter(job -> ObjectNull.isNull(job.getMergeTaskCode()))
                .collect(Collectors.toList());
    }
}
