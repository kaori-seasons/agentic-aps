package cn.windwheel.aps.solve.score;

import ai.timefold.solver.core.api.score.buildin.hardsoftlong.HardSoftLongScore;
import ai.timefold.solver.core.api.score.stream.*;
import cn.windwheel.aps.solve.model.ProductionTask;
import cn.windwheel.common.utils.ObjectNull;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Objects;

/** Timefold Constraint Streams counterpart of the existing APS hard/soft objectives. */
public final class TimefoldConstraintProvider extends SolverConstraintWeights implements ConstraintProvider {
    @Override
    public Constraint[] defineConstraints(ConstraintFactory factory) {
        return new Constraint[] {
                arrangeByPriority(factory), matchingResource(factory), finishEarly(factory), minimizeTardiness(factory),
                balanceResourceLoad(factory), dependencyGap(factory), noOverlap(factory), assignedAndTimed(factory), setup(factory)
        };
    }

    private static boolean scheduled(ProductionTask task) {
        return ObjectNull.isNull(task.getMergeTaskCode()) && task.getResource() != null
                && task.getStartTime() != null && task.getEndTime() != null;
    }

    public Constraint arrangeByPriority(ConstraintFactory factory) {
        return factory.forEach(ProductionTask.class).filter(TimefoldConstraintProvider::scheduled)
                .join(factory.forEach(ProductionTask.class).filter(TimefoldConstraintProvider::scheduled),
                        Joiners.equal(ProductionTask::getResource))
                .filter((a, b) -> !Boolean.TRUE.equals(a.getPinned()) && !Boolean.TRUE.equals(b.getPinned())
                        && a.getPriority() != null && b.getPriority() != null
                        && a.getPriority() < b.getPriority() && a.getStartTime().isAfter(b.getStartTime()))
                .penalizeLong(HardSoftLongScore.ONE_SOFT, (a, b) -> ((long) b.getPriority() - a.getPriority()) * getPriorityWeight())
                .asConstraint("尽量按优先级安排");
    }

    public Constraint matchingResource(ConstraintFactory factory) {
        return factory.forEach(ProductionTask.class)
                .filter(task -> ObjectNull.isNull(task.getMergeTaskCode()) && task.getResource() != null)
                .filter(task -> task.getProcess() == null || task.getProcess().getUseMainResources() == null
                        || task.getProcess().getUseMainResources().stream()
                        .noneMatch(resource -> Objects.equals(resource.getId(), task.getResource().getId())))
                .penalize(HardSoftLongScore.ONE_HARD).asConstraint("主资源只能处理匹配的工序");
    }

    public Constraint finishEarly(ConstraintFactory factory) {
        return factory.forEach(ProductionTask.class).filter(TimefoldConstraintProvider::scheduled)
                .groupBy(ConstraintCollectors.max(ProductionTask::getEndTime))
                .penalizeLong(HardSoftLongScore.ONE_SOFT, end -> Math.max(0, end.toEpochSecond(ZoneOffset.UTC)) * getFinishEarlyWeight())
                .asConstraint("尽早完成任务");
    }

    public Constraint minimizeTardiness(ConstraintFactory factory) {
        return factory.forEach(ProductionTask.class).filter(TimefoldConstraintProvider::scheduled)
                .filter(task -> task.getOrder() != null && task.getOrder().getDeliveryTime() != null)
                .penalizeLong(HardSoftLongScore.ONE_SOFT, task -> Math.max(0,
                        Duration.between(task.getOrder().getDeliveryTime(), task.getEndTime()).toMinutes()) * getMinimizeTardinessWeight())
                .asConstraint("最小化逾期");
    }

    public Constraint balanceResourceLoad(ConstraintFactory factory) {
        return factory.forEach(ProductionTask.class).filter(TimefoldConstraintProvider::scheduled)
                .groupBy(ProductionTask::getResource, ConstraintCollectors.sumLong(task -> Math.max(0, task.calculateTaskDuration().toMinutes())))
                .penalizeLong(HardSoftLongScore.ONE_SOFT, (resource, minutes) -> minutes * minutes * getBalanceResourceLoadWeight())
                .asConstraint("资源负载均衡");
    }

    public Constraint dependencyGap(ConstraintFactory factory) {
        // Joining predecessors makes their changes observable to incremental scoring.
        return factory.forEach(ProductionTask.class).filter(TimefoldConstraintProvider::scheduled)
                .join(factory.forEach(ProductionTask.class).filter(TimefoldConstraintProvider::scheduled),
                        Joiners.filtering((task, predecessor) -> task.getFrontTaskCodes() != null
                                && task.getFrontTaskCodes().contains(predecessor.getCode())))
                .groupBy((task, predecessor) -> task.getId(), ConstraintCollectors.max((task, predecessor) -> predecessor.getEndTime()))
                .join(factory.forEach(ProductionTask.class).filter(TimefoldConstraintProvider::scheduled),
                        Joiners.equal((taskId, latestEnd) -> taskId, ProductionTask::getId))
                .penalizeLong(HardSoftLongScore.ONE_SOFT, (taskId, latestEnd, task) -> Math.max(0,
                        Duration.between(latestEnd, task.getStartTime()).toMinutes()) * getMinimizeGapBetweenDependentTasksWeight())
                .asConstraint("优化任务调度的连续性");
    }

    public Constraint noOverlap(ConstraintFactory factory) {
        return factory.forEach(ProductionTask.class).filter(TimefoldConstraintProvider::scheduled)
                .join(factory.forEach(ProductionTask.class).filter(TimefoldConstraintProvider::scheduled),
                        Joiners.equal(ProductionTask::getResource),
                        Joiners.lessThan(ProductionTask::getId),
                        Joiners.overlapping(ProductionTask::getStartTime, ProductionTask::getEndTime))
                .penalizeLong(HardSoftLongScore.ONE_HARD, (a, b) -> {
                    LocalDateTime start = a.getStartTime().isAfter(b.getStartTime()) ? a.getStartTime() : b.getStartTime();
                    LocalDateTime end = a.getEndTime().isBefore(b.getEndTime()) ? a.getEndTime() : b.getEndTime();
                    // Keep millisecond precision without the legacy int overflow on long tasks.
                    return Math.max(1, Duration.between(start, end).toMillis());
                }).asConstraint("资源没有任务时间重叠");
    }

    public Constraint assignedAndTimed(ConstraintFactory factory) {
        return factory.forEachIncludingUnassigned(ProductionTask.class)
                .filter(task -> ObjectNull.isNull(task.getMergeTaskCode()) && !scheduled(task))
                .penalize(HardSoftLongScore.ONE_HARD).asConstraint("每个任务都要分配资源");
    }

    public Constraint setup(ConstraintFactory factory) {
        return factory.forEach(ProductionTask.class).filter(TimefoldConstraintProvider::scheduled)
                .join(factory.forEach(ProductionTask.class).filter(TimefoldConstraintProvider::scheduled),
                        Joiners.equal(ProductionTask::getResource))
                // Retains the legacy material-switch objective for non-overlapping task pairs.
                .filter((a, b) -> a.getEndTime().isBefore(b.getStartTime())
                        && a.getMaterial() != null && b.getMaterial() != null
                        && !Objects.equals(a.getMaterial().getId(), b.getMaterial().getId()))
                .penalizeLong(HardSoftLongScore.ONE_SOFT, (a, b) -> getMinimizeSetupTimeWeight())
                .asConstraint("最小化换型时间");
    }
}
