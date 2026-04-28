package cn.windwheel.aps.solve.score;

import cn.windwheel.aps.solve.model.ProductionTask;
import cn.windwheel.common.utils.ObjectNull;
import lombok.extern.slf4j.Slf4j;
import org.optaplanner.core.api.score.buildin.hardsoftlong.HardSoftLongScore;
import org.optaplanner.core.api.score.stream.*;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.Optional;

/**
 * @author jvs
 * 约束
 */
@Slf4j
public class ApsHardSoftScoreConstraintProvider implements ConstraintProvider {

    @Override
    public Constraint[] defineConstraints(ConstraintFactory constraintFactory) {
        return new Constraint[] {
                arrangeByPriority(constraintFactory),
                equipmentCanOnlyHandleMatchingProcesses(constraintFactory),
                finishEarly(constraintFactory),
                minimizeTardiness(constraintFactory),
                balanceResourceLoad(constraintFactory),
                minimizeGapBetweenDependentTasks(constraintFactory),
                noResourceTimeOverlapping(constraintFactory),
                theEquipmentCanOnlyProcessOneProcessAtTheSameTime(constraintFactory),
                minimizeSetupTime(constraintFactory)
        };
    }

    public Constraint arrangeByPriority(ConstraintFactory factory) {
        return factory.forEach(ProductionTask.class)
                .join(ProductionTask.class,
                        Joiners.equal(ProductionTask::getResource),
                        Joiners.lessThan(ProductionTask::getPriority)
                )
                .filter((task1, task2) -> !Boolean.TRUE.equals(task1.getPinned()) && !Boolean.TRUE.equals(task2.getPinned()))
                .filter((task1, task2) -> ObjectNull.isNotNull(task1.getStartTime()) && ObjectNull.isNotNull(task2.getStartTime()))
                .filter((task1, task2) -> ObjectNull.isNull(task1.getMergeTaskCode()) && ObjectNull.isNull(task2.getMergeTaskCode()))
                .filter((task1, task2) -> task1.getStartTime().isAfter(task2.getStartTime()))
                .penalizeLong(HardSoftLongScore.ONE_SOFT, (task1, task2) -> {
                    long weight = DynamicConstraintWeightContext.getWeightConfig().getPriorityWeight();
                    return (long) (task2.getPriority() - task1.getPriority()) * weight;
                })
                .asConstraint("尽量按优先级安排");
    }

    public Constraint equipmentCanOnlyHandleMatchingProcesses(ConstraintFactory factory) {
        return factory.forEach(ProductionTask.class)
                .filter(productionJob -> ObjectNull.isNotNull(productionJob.getResource()))
                .filter(productionJob -> productionJob.getProcess().getUseMainResources()
                        .stream()
                        .noneMatch(resource -> productionJob.getResource().getId().equals(resource.getId())))
                .penalize(HardSoftLongScore.ONE_HARD)
                .asConstraint("主资源只能处理匹配的工序");
    }

    public Constraint finishEarly(ConstraintFactory factory) {
        return factory.forEach(ProductionTask.class)
                .filter(task -> task.getStartTime() != null && task.getEndTime() != null && ObjectNull.isNull(task.getMergeTaskCode()))
                .groupBy(ConstraintCollectors.max(ProductionTask::getEndTime))
                .penalizeLong(HardSoftLongScore.ONE_SOFT, maxEndTime -> {
                    long weight = DynamicConstraintWeightContext.getWeightConfig().getFinishEarlyWeight();
                    return maxEndTime.toEpochSecond(ZoneOffset.UTC) * weight;
                })
                .asConstraint("尽早完成任务");
    }

    public Constraint minimizeTardiness(ConstraintFactory factory) {
        return factory.forEach(ProductionTask.class)
                .filter(task -> ObjectNull.isNull(task.getMergeTaskCode()))
                .filter(task -> ObjectNull.isNotNull(task.getEndTime()))
                .filter(task -> ObjectNull.isNotNull(task.getOrder()))
                .filter(task -> ObjectNull.isNotNull(task.getOrder().getDeliveryTime()))
                .penalizeLong(HardSoftLongScore.ONE_SOFT, task -> {
                    long delayMinutes = Duration.between(task.getOrder().getDeliveryTime(), task.getEndTime()).toMinutes();
                    long weight = DynamicConstraintWeightContext.getWeightConfig().getMinimizeTardinessWeight();
                    return Math.max(delayMinutes, 0L) * weight;
                })
                .asConstraint("最小化逾期");
    }

    public Constraint balanceResourceLoad(ConstraintFactory factory) {
        return factory.forEach(ProductionTask.class)
                .filter(task -> ObjectNull.isNull(task.getMergeTaskCode()))
                .filter(task -> ObjectNull.isNotNull(task.getResource()))
                .groupBy(ProductionTask::getResource, ConstraintCollectors.sumLong(this::taskDurationInMinutes))
                .penalizeLong(HardSoftLongScore.ONE_SOFT, (resource, load) -> {
                    long weight = DynamicConstraintWeightContext.getWeightConfig().getBalanceResourceLoadWeight();
                    return load * load * weight;
                })
                .asConstraint("资源负载均衡");
    }

    public Constraint minimizeGapBetweenDependentTasks(ConstraintFactory factory) {
        return factory.forEach(ProductionTask.class)
                .filter(task -> task.getStartTime() != null && task.getEndTime() != null && ObjectNull.isNull(task.getMergeTaskCode()))
                .filter(task -> {
                    if (ObjectNull.isNull(task.getFrontTasks())) {
                        return false;
                    }
                    return task.getFrontTasks().stream().noneMatch(t -> ObjectNull.isNull(t.getStartTime()));
                })
                .penalizeLong(HardSoftLongScore.ONE_SOFT, (task) -> {
                    LocalDateTime frontTaskMaxEndTime = task.getFrontTasks().stream().map(ProductionTask::getEndTime).max(Comparator.naturalOrder()).get();
                    Duration duration = Duration.between(frontTaskMaxEndTime, task.getStartTime());
                    long weight = DynamicConstraintWeightContext.getWeightConfig().getMinimizeGapBetweenDependentTasksWeight();
                    return (duration.toMinutes() > 0 ? duration.toMinutes() : 0) * weight;
                })
                .asConstraint("优化任务调度的连续性");
    }

    public Constraint noResourceTimeOverlapping(ConstraintFactory factory) {
        return factory.forEach(ProductionTask.class)
                .join(ProductionTask.class,
                        Joiners.equal(ProductionTask::getResource),
                        Joiners.overlapping(task -> Optional.ofNullable(task.getStartTime()).orElseGet(() -> LocalDateTime.MIN), task -> Optional.ofNullable(task.getEndTime()).orElseGet(() -> LocalDateTime.MAX))
                )
                .filter((task1, task2) -> ObjectNull.isNull(task1.getMergeTaskCode()) && ObjectNull.isNull(task2.getMergeTaskCode()))
                .filter((task1, task2) -> ObjectNull.isNotNull(task1.getStartTime()) && ObjectNull.isNotNull(task2.getStartTime()))
                .filter((task1, task2) -> {
                    if (task1.getCode().equals(task2.getCode())) {
                        return false;
                    }
                    return task1.getEndTime().isAfter(task2.getStartTime()) && task1.getStartTime().isBefore(task2.getEndTime());
                })
                .penalize(HardSoftLongScore.ONE_HARD, ApsHardSoftScoreConstraintProvider::getMinuteOverlap)
                .asConstraint("资源没有任务时间重叠");
    }

    public Constraint minimizeSetupTime(ConstraintFactory factory) {
        return factory.forEach(ProductionTask.class)
                .filter(task -> ObjectNull.isNotNull(task.getStartTime()) && ObjectNull.isNotNull(task.getResource()) && ObjectNull.isNotNull(task.getEndTime()))
                .join(ProductionTask.class,
                        Joiners.equal(ProductionTask::getResource)
                )
                .filter((task1, task2) -> {
                    if (ObjectNull.isNull(task2.getStartTime()) || ObjectNull.isNull(task2.getEndTime())) {
                        return false;
                    }
                    // task1.endTime < task2.startTime (non-overlapping sequential tasks)
                    if (!task1.getEndTime().isBefore(task2.getStartTime())) {
                        return false;
                    }
                    // 只计算相邻的两个任务：假设如果它们在同一个资源上，且没有第三个任务插在中间
                    // 由于没有显式的 nextTask 关联，只能近似惩罚物料不同带来的切换
                    return ObjectNull.isNotNull(task1.getMaterial()) && ObjectNull.isNotNull(task2.getMaterial())
                            && !task1.getMaterial().getId().equals(task2.getMaterial().getId());
                })
                .penalizeLong(HardSoftLongScore.ONE_SOFT, (task1, task2) -> {
                    // 动态获取针对换型时间的惩罚权重
                    long weight = DynamicConstraintWeightContext.getWeightConfig().getMinimizeSetupTimeWeight();
                    return weight;
                })
                .asConstraint("最小化换型时间");
    }

    /**
     * 获取冲突分钟数
     *
     * @param leftTask 左任务
     * @param rightTask 右任务
     * @return 冲突分钟数
     */
    private static int getMinuteOverlap(ProductionTask leftTask, ProductionTask rightTask) {
        LocalDateTime leftStart = leftTask.getStartTime();
        LocalDateTime leftEnd = leftTask.getEndTime();
        LocalDateTime rightStart = rightTask.getStartTime();
        LocalDateTime rightEnd = rightTask.getEndTime();
        LocalDateTime start = leftStart.compareTo(rightStart) > 0 ? leftStart : rightStart;
        LocalDateTime end = leftEnd.compareTo(rightEnd) < 0 ? leftEnd : rightEnd;
        return (int) Duration.between(start, end).toMillis();
    }

    private long taskDurationInMinutes(ProductionTask task) {
        Duration duration = task.calculateTaskDuration();
        if (ObjectNull.isNull(duration)) {
            return 0L;
        }
        return Math.max(duration.toMinutes(), 0L);
    }

    public Constraint theEquipmentCanOnlyProcessOneProcessAtTheSameTime(ConstraintFactory factory) {
        return factory.forEach(ProductionTask.class)
                .filter(job -> job.getResource() == null || job.getStartTime() == null || job.getEndTime() == null)
                .penalize(HardSoftLongScore.ONE_HARD)
                .asConstraint("每个任务都要分配资源");
    }
}
