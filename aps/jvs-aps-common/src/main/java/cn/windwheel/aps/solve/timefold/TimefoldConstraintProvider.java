package cn.windwheel.aps.solve.timefold;

import cn.windwheel.aps.solve.score.SchedulingRules;

import cn.windwheel.aps.solve.model.ProductionTask;
import java.time.Duration;
import ai.timefold.solver.core.api.score.buildin.hardsoftlong.HardSoftLongScore;
import ai.timefold.solver.core.api.score.stream.*;

public class TimefoldConstraintProvider implements ConstraintProvider {
    @Override
    public Constraint[] defineConstraints(ConstraintFactory factory) {
        return new Constraint[] {
            factory.forEachIncludingUnassigned(ProductionTask.class)
                .filter(SchedulingRules::active)
                .filter(task -> !SchedulingRules.scheduled(task))
                .penalize(HardSoftLongScore.ONE_HARD).asConstraint("每个任务都要分配资源"),
            factory.forEach(ProductionTask.class)
                .filter(task -> SchedulingRules.active(task) && task.getResource() != null)
                .filter(task -> task.getProcess().getUseMainResources().stream()
                    .noneMatch(resource -> resource.getId().equals(task.getResource().getId())))
                .penalize(HardSoftLongScore.ONE_HARD).asConstraint("主资源只能处理匹配的工序"),
            factory.forEachUniquePair(ProductionTask.class, Joiners.equal(ProductionTask::getResource))
                .filter((a,b) -> SchedulingRules.active(a) && SchedulingRules.active(b)
                    && SchedulingRules.scheduled(a) && SchedulingRules.scheduled(b))
                .filter((a,b) -> SchedulingRules.overlap(a.getStartTime(),a.getEndTime(),b.getStartTime(),b.getEndTime()))
                .penalizeLong(HardSoftLongScore.ONE_HARD, SchedulingRules::overlapMinutes)
                .asConstraint("资源没有任务时间重叠"),
            factory.forEach(ProductionTask.class)
                .join(ProductionTask.class, Joiners.filtering((task,front) ->
                    task.getFrontTaskCodes() != null && task.getFrontTaskCodes().contains(front.getCode())))
                .filter(SchedulingRules::dependencyBroken)
                .penalize(HardSoftLongScore.ONE_HARD).asConstraint("工序前后置关系"),
            factory.forEach(ProductionTask.class)
                .filter(task -> SchedulingRules.active(task) && task.getNotBefore() != null && task.getStartTime() != null
                    && task.getStartTime().isBefore(task.getNotBefore()))
                .penalize(HardSoftLongScore.ONE_HARD).asConstraint("物料最早可用时间"),
            factory.forEach(ProductionTask.class).filter(SchedulingRules::unavailable)
                .penalize(HardSoftLongScore.ONE_HARD).asConstraint("资源不可用时间窗"),
            factory.forEach(ProductionTask.class).filter(SchedulingRules::frozenChanged)
                .penalize(HardSoftLongScore.ONE_HARD).asConstraint("冻结任务时间不变"),
            factory.forEach(ProductionTask.class)
                .join(ProductionTask.class, Joiners.equal(ProductionTask::getResource),
                    Joiners.lessThan(task -> task.getPriority() == null ? Integer.MAX_VALUE : task.getPriority()))
                .filter((a,b) -> SchedulingRules.active(a) && SchedulingRules.active(b)
                    && SchedulingRules.scheduled(a) && SchedulingRules.scheduled(b)
                    && !Boolean.TRUE.equals(a.getPinned()) && !Boolean.TRUE.equals(b.getPinned())
                    && a.getStartTime().isAfter(b.getStartTime()))
                .penalizeLong(HardSoftLongScore.ONE_SOFT, (a,b) ->
                    (long)(b.getPriority()-a.getPriority())*a.getWeights().getPriorityWeight())
                .asConstraint("尽量按优先级安排"),
            factory.forEach(ProductionTask.class)
                .filter(task -> SchedulingRules.active(task) && SchedulingRules.scheduled(task))
                .groupBy(ProductionTask::getWeights, ConstraintCollectors.max(ProductionTask::getEndTime))
                .penalizeLong(HardSoftLongScore.ONE_SOFT, (weights,end) ->
                    end.toEpochSecond(java.time.ZoneOffset.UTC)*weights.getFinishEarlyWeight())
                .asConstraint("尽早完成任务"),
            factory.forEach(ProductionTask.class)
                .filter(task -> SchedulingRules.active(task) && Boolean.TRUE.equals(task.getEndTask())
                    && SchedulingRules.scheduled(task) && task.getOrder()!=null && task.getOrder().getDeliveryTime()!=null)
                .penalizeLong(HardSoftLongScore.ONE_SOFT, task ->
                    Math.max(0,Duration.between(task.getOrder().getDeliveryTime(),task.getEndTime()).toMinutes())
                        *task.getWeights().getMinimizeTardinessWeight())
                .asConstraint("最小化逾期"),
            factory.forEach(ProductionTask.class)
                .filter(task -> SchedulingRules.active(task) && SchedulingRules.scheduled(task))
                .groupBy(ProductionTask::getResource, ProductionTask::getWeights,
                    ConstraintCollectors.sumLong(SchedulingRules::durationMinutes))
                .penalizeLong(HardSoftLongScore.ONE_SOFT, (resource,weights,load) ->
                    load*load*weights.getBalanceResourceLoadWeight())
                .asConstraint("资源负载均衡"),
            factory.forEach(ProductionTask.class)
                .filter(task -> SchedulingRules.active(task) && SchedulingRules.scheduled(task)
                    && task.getFrontTasks()!=null && !task.getFrontTasks().isEmpty()
                    && task.getFrontTasks().stream().allMatch(SchedulingRules::scheduled))
                .penalizeLong(HardSoftLongScore.ONE_SOFT, task -> {
                    java.time.LocalDateTime end = task.getFrontTasks().stream()
                        .map(ProductionTask::getEndTime).max(java.util.Comparator.naturalOrder()).orElseThrow();
                    return Math.max(0,Duration.between(end,task.getStartTime()).toMinutes())
                        *task.getWeights().getMinimizeGapBetweenDependentTasksWeight();
                }).asConstraint("优化任务调度的连续性"),
            factory.forEach(ProductionTask.class)
                .join(ProductionTask.class, Joiners.equal(ProductionTask::getResource))
                .filter(SchedulingRules::orderedDifferentMaterial)
                .ifNotExists(ProductionTask.class, Joiners.equal((a,b) -> a.getResource(), ProductionTask::getResource),
                    Joiners.filtering(SchedulingRules::between))
                .penalizeLong(HardSoftLongScore.ONE_SOFT, (a,b) -> a.getWeights().getMinimizeSetupTimeWeight())
                .asConstraint("最小化换型时间")
        };
    }
}
