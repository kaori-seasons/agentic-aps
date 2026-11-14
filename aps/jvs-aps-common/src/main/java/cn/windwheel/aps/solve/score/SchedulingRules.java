package cn.windwheel.aps.solve.score;

import cn.windwheel.aps.entity.enums.ProcessRelationshipEnum;
import cn.windwheel.aps.solve.model.*;
import cn.windwheel.aps.solve.util.DurationUtils;
import java.time.*;
import java.util.Objects;

/** 两个求解引擎与结果门禁共用的业务语义。时间区间使用左闭右开。 */
public final class SchedulingRules {
    private SchedulingRules() {}

    public static boolean active(ProductionTask task) {
        return task.getMergeTaskCode() == null || task.getMergeTaskCode().isEmpty();
    }

    public static boolean scheduled(ProductionTask task) {
        return task.getResource() != null && task.getStartTime() != null && task.getEndTime() != null;
    }

    public static boolean overlap(LocalDateTime a, LocalDateTime b, LocalDateTime c, LocalDateTime d) {
        return a != null && b != null && c != null && d != null && a.isBefore(d) && c.isBefore(b);
    }

    public static long overlapMinutes(ProductionTask a, ProductionTask b) {
        LocalDateTime start = a.getStartTime().isAfter(b.getStartTime()) ? a.getStartTime() : b.getStartTime();
        LocalDateTime end = a.getEndTime().isBefore(b.getEndTime()) ? a.getEndTime() : b.getEndTime();
        return Math.max(1, Duration.between(start, end).toMinutes());
    }

    public static boolean dependencyBroken(ProductionTask task, ProductionTask front) {
        if (!scheduled(task) || !scheduled(front)) { return false; }
        Duration pre = DurationUtils.convertDuration(task.getProcess().getPreIntervalDuration());
        if (!Boolean.TRUE.equals(task.getStartTask()) && ProcessRelationshipEnum.EE.equals(task.getProcess().getProcessRelationship())) {
            Duration buffer = DurationUtils.convertDuration(task.getProcess().getBufferTime());
            return task.getStartTime().isBefore(front.getStartTime().plus(pre).plus(buffer))
                    || task.getEndTime().isBefore(front.getEndTime().plus(buffer));
        }
        Duration post = DurationUtils.convertDuration(front.getProcess().getPostIntervalDuration());
        return task.getStartTime().isBefore(front.getEndTime().plus(pre).plus(post));
    }

    public static boolean unavailable(ProductionTask task) {
        return scheduled(task) && task.getResource().getDowntimes().stream()
                .anyMatch(window -> overlap(task.getStartTime(), task.getEndTime(), window.getStart(), window.getEnd()));
    }

    public static boolean frozenChanged(ProductionTask task) {
        return Boolean.TRUE.equals(task.getPinned()) && task.getFrozenStartTime() != null
                && (task.getResource() == null || !Objects.equals(task.getFrozenResourceId(), task.getResource().getId())
                || !Objects.equals(task.getFrozenStartTime(), task.getStartTime())
                || !Objects.equals(task.getFrozenEndTime(), task.getEndTime()));
    }

    public static long durationMinutes(ProductionTask task) {
        Duration duration = task.calculateTaskDuration();
        return duration == null ? 0 : Math.max(0, duration.toMinutes());
    }

    public static boolean orderedDifferentMaterial(ProductionTask a, ProductionTask b) {
        return scheduled(a) && scheduled(b) && active(a) && active(b) && a != b
                && !a.getEndTime().isAfter(b.getStartTime()) && a.getMaterial() != null && b.getMaterial() != null
                && !Objects.equals(a.getMaterial().getId(), b.getMaterial().getId());
    }

    public static boolean between(ProductionTask a, ProductionTask b, ProductionTask middle) {
        return scheduled(a) && scheduled(b) && middle != a && middle != b && scheduled(middle) && active(middle)
                && !middle.getStartTime().isBefore(a.getEndTime()) && !middle.getEndTime().isAfter(b.getStartTime());
    }
}
