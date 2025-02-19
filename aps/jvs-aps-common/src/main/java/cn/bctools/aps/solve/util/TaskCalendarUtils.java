package cn.bctools.aps.solve.util;

import cn.bctools.aps.entity.enums.PlanningDirectionEnum;
import cn.bctools.aps.enums.DayOfWeekEnum;
import cn.bctools.aps.solve.model.WorkCalendar;
import cn.bctools.aps.solve.model.WorkModeTime;
import cn.bctools.common.exception.BusinessException;
import cn.bctools.common.utils.ObjectNull;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;

/**
 * @author jvs
 * 任务排产使用日历
 */
@Slf4j
public class TaskCalendarUtils {

    private TaskCalendarUtils() {
    }


    /**
     * 根据日历计算工作日内的任务开始时间
     *
     * @param earliestStartTime 任务最早开始时间
     * @param workCalendars     日历集合
     * @param planningDirection 排产方向
     * @return 任务开始时间
     */
    public static LocalDateTime calculateStartTime(LocalDateTime earliestStartTime, List<WorkCalendar> workCalendars, PlanningDirectionEnum planningDirection) {
        // 资源未设置日历，直接返回开始时间
        if (ObjectNull.isNull(workCalendars)) {
            return earliestStartTime;
        }

        // 获取生效的日历。 若没有生效的日历，直接返回开始时间
        List<WorkCalendar> effectiveCalendars = getEffectiveCalendar(earliestStartTime, workCalendars);
        if (ObjectNull.isNull(effectiveCalendars)) {
            return earliestStartTime;
        }

        // 根据日历计算开始时间 —— 正排
        if (PlanningDirectionEnum.FORWARD.equals(planningDirection)) {
            return Forward.calculateStartTime(earliestStartTime, workCalendars);
        }

        throw new BusinessException("系统异常");
    }

    /**
     * 根据日历计算工作日内的任务结束时间
     *
     * @param startTime     任务开始时间
     * @param taskDuration  任务持续时长（不包括休息时间）
     * @param workCalendars 日历集合
     * @return 任务结束时间 (可跨越休息时间)
     */
    public static LocalDateTime calculateEndTime(LocalDateTime startTime, Duration taskDuration, List<WorkCalendar> workCalendars) {
        // 结束时间
        LocalDateTime endTime = null;
        // 根据日历找到合适的结束时间（工作时间）
        Queue<LocalDateTime> queue = new ArrayDeque<>();
        queue.add(startTime);
        while (!queue.isEmpty()) {
            // 待计算的时间
            LocalDateTime time = queue.remove();
            // 获取生效的日历
            List<WorkCalendar> effectiveCalendars = getEffectiveCalendar(time, workCalendars);
            if (ObjectNull.isNull(effectiveCalendars)) {
                // 没有生效的日历
                // 临时计算结束时间
                LocalDateTime tempEndTime = time.plus(taskDuration);
                if (tempEndTime.toLocalDate().equals(time.toLocalDate())) {
                    endTime = tempEndTime;
                } else {
                    LocalDateTime nextTime = tomorrowAtMidnight(time);
                    taskDuration = taskDuration.minus(Duration.between(time, nextTime));
                    queue.add(nextTime);
                }
                continue;
            }

            // 获取使用的日历
            WorkCalendar useWorkCalendar = getUseCalendar(effectiveCalendars);

            // 计算结束时间
            if (ObjectNull.isNull(useWorkCalendar.getWorkModeTimes())) {
                LocalDateTime nextTime = tomorrowAtMidnight(time);
                queue.add(nextTime);
            } else {
                // 找最近的工作时间
                LocalDateTime recentWorkTime = findRecentWorkTime(time, useWorkCalendar);
                if (ObjectNull.isNotNull(recentWorkTime)) {
                    WorkModeTime workModeTime = getUseWorkTime(useWorkCalendar.getWorkModeTimes(), recentWorkTime.toLocalTime());
                    Duration remainingWorking = Duration.between(recentWorkTime.toLocalTime(), workModeTime.getEnd());
                    if (remainingWorking.compareTo(taskDuration) >= 0) {
                        endTime = recentWorkTime.plus(taskDuration);
                    } else {
                        taskDuration = taskDuration.minus(remainingWorking);
                        LocalDateTime nextTime = recentWorkTime.with(workModeTime.getEnd());
                        queue.add(nextTime);
                    }
                } else {
                    // 将时间延后到下一天，继续计算结束时间
                    LocalDateTime nextTime = tomorrowAtMidnight(time);
                    queue.add(nextTime);
                }
            }
        }
        return endTime;
    }


    /**
     * 获取生效的日历
     *
     * @param time          时间
     * @param workCalendars 日历集合
     * @return 生效的日历
     */
    private static List<WorkCalendar> getEffectiveCalendar(LocalDateTime time, List<WorkCalendar> workCalendars) {
        if (ObjectNull.isNull(workCalendars)) {
            return Collections.emptyList();
        }
        return workCalendars.stream()
                .filter(calendar ->
                        (calendar.getBeginTime().isBefore(time) || calendar.getBeginTime().isEqual(time))
                                &&
                                (calendar.getEndTime().isAfter(time) || calendar.getEndTime().isEqual(time))
                )
                .filter(calendar -> {
                    DayOfWeekEnum dayOfWeekEnum = DayOfWeekEnum.getDayOfWeekEnumByName(time.getDayOfWeek().name());
                    return calendar.getWorkDaySetting().get(dayOfWeekEnum);
                })
                .toList();
    }


    /**
     * 获取使用的日历
     *
     * @param effectiveCalendars 生效的日历集合
     * @return 使用的日历
     */
    private static WorkCalendar getUseCalendar(List<WorkCalendar> effectiveCalendars) {
        if (effectiveCalendars.size() == 1) {
            return effectiveCalendars.get(0);
        }
        // 若有多个生效的日历，使用优先级最高的
        Optional<WorkCalendar> optionalWorkCalendar = effectiveCalendars.stream()
                .max(Comparator.comparing(WorkCalendar::getPriority));
        return optionalWorkCalendar.orElse(null);
    }

    /**
     * 获取时间所在工作模式起止时间
     *
     * @param workModeTimes 工作模式
     * @param time          时间
     * @return 工作模式起止时间（为空时，表示该时间不在工作时间段中）
     */
    private static WorkModeTime getUseWorkTime(List<WorkModeTime> workModeTimes, LocalTime time) {
        Optional<WorkModeTime> optionalWorkModeTime = workModeTimes.stream()
                .filter(workModeTime -> {
                    // 工作模式的区间的开始时间 在 区间的结束时间之前
                    if (workModeTime.getStart().isBefore(workModeTime.getEnd())) {
                        return (workModeTime.getStart().isBefore(time) || workModeTime.getStart().equals(time))
                                &&
                                workModeTime.getEnd().isAfter(time);
                    } else {
                        return ((workModeTime.getStart().isBefore(time) || workModeTime.getStart().equals(time))
                                &&
                                LocalTime.MAX.isAfter(time))
                                &&
                                ((LocalTime.MIN.isBefore(time) || LocalTime.MIN.equals(time))
                                        &&
                                        workModeTime.getEnd().isAfter(time));
                    }
                })
                .findFirst();
        return optionalWorkModeTime.orElse(null);
    }

    /**
     * 明天零点
     *
     * @param time 时间
     * @return 开始时间
     */
    private static LocalDateTime tomorrowAtMidnight(LocalDateTime time) {
        return time.plusDays(1).with(LocalTime.MIDNIGHT);
    }

    /**
     * 寻找最近的工作时间
     * <p>
     * 指定日期时间当天最近的工作时间
     *
     * @param dateTime 时间
     * @return 最近的工作时间
     */
    public static LocalDateTime findRecentWorkTime(LocalDateTime dateTime, WorkCalendar useWorkCalendar) {
        // 已配置工作模式，根据工作模式，找最近的可开工时间
        LocalTime time = dateTime.toLocalTime();

        // 获取时间所在工作模式起止时间
        WorkModeTime workTime = getUseWorkTime(useWorkCalendar.getWorkModeTimes(), time);

        // 开始时间在工作时间内
        if (ObjectNull.isNotNull(workTime)) {
            return dateTime;
        }

        // 开始时间不在工作时间内，找到下一个最近的开工时间
        LocalDateTime nextStartTime = null;
        for (WorkModeTime workModeTime : useWorkCalendar.getWorkModeTimes()) {
            if (time.isBefore(workModeTime.getStart())) {
                nextStartTime = dateTime.with(workModeTime.getStart());
                break;
            }
        }
        return nextStartTime;
    }


    /**
     * 计算时间区间内的总工作时长
     *
     * @param startTime     开始时间
     * @param endTime       结束时间
     * @param workCalendars 日历
     * @return 指定时间区间内的总工作时长
     */
    public static Duration workDuration(LocalDateTime startTime, LocalDateTime endTime, List<WorkCalendar> workCalendars) {
        // 总工作时长
        Duration totalDuration = Duration.ZERO;
        // 根据日历找到合适的结束时间（工作时间）
        Queue<LocalDateTime> queue = new ArrayDeque<>();
        queue.add(startTime);
        while (!queue.isEmpty()) {
            LocalDateTime time = queue.remove();
            if (time.isAfter(endTime) || time.isEqual(endTime)) {
                continue;
            }
            // 获取生效的日历
            List<WorkCalendar> effectiveCalendars = getEffectiveCalendar(time, workCalendars);
            if (ObjectNull.isNull(effectiveCalendars)) {
                // 没有生效的日历
                if (endTime.toLocalDate().equals(time.toLocalDate())) {
                    totalDuration = totalDuration.plus(Duration.between(time, endTime));
                } else {
                    LocalDateTime nextTime = tomorrowAtMidnight(time);
                    totalDuration = totalDuration.plus(Duration.between(time, nextTime));
                    queue.add(nextTime);
                }
                continue;
            }

            // 获取使用的日历
            WorkCalendar useWorkCalendar = getUseCalendar(effectiveCalendars);

            // 计算结束时间
            if (ObjectNull.isNull(useWorkCalendar.getWorkModeTimes())) {
                LocalDateTime nextTime = tomorrowAtMidnight(time);
                queue.add(nextTime);
            } else {
                // 找最近的工作时间
                LocalDateTime recentWorkTime = findRecentWorkTime(time, useWorkCalendar);
                if (ObjectNull.isNull(recentWorkTime)) {
                    LocalDateTime nextTime = tomorrowAtMidnight(time);
                    queue.add(nextTime);
                    continue;
                }
                // 获取时间所属工作时间段
                WorkModeTime workModeTime = getUseWorkTime(useWorkCalendar.getWorkModeTimes(), recentWorkTime.toLocalTime());
                // 该工作时间段剩余可工作时长
                LocalTime workModelEndTime = workModeTime.getEnd();
                // 结束时间与待计算的时间是同一天
                if (endTime.toLocalDate().equals(time.toLocalDate())) {
                    if (!endTime.toLocalTime().isAfter(recentWorkTime.toLocalTime())) {
                        continue;
                    }
                    if (!endTime.toLocalTime().isAfter(workModelEndTime)) {
                        workModelEndTime = endTime.toLocalTime();
                    }
                }
                Duration remainingWorking = Duration.between(recentWorkTime.toLocalTime(), workModelEndTime);
                totalDuration = totalDuration.plus(remainingWorking);

                // 继续计算工作时长
                LocalDateTime nextTime = recentWorkTime.with(workModeTime.getEnd());
                queue.add(nextTime);
            }
        }
        return totalDuration;
    }

    /**
     * 根据日历计算开始时间 —— 正排
     */
    private static class Forward {
        /**
         * 计算开始时间
         *
         * @param earliestStartTime 任务最早开始时间
         * @param workCalendars     当前资源资源所有日历集合
         * @return 开始时间
         */
        public static LocalDateTime calculateStartTime(LocalDateTime earliestStartTime, List<WorkCalendar> workCalendars) {
            // 开始时间
            LocalDateTime startTime = null;
            Queue<LocalDateTime> queue = new ArrayDeque<>();
            queue.add(earliestStartTime);
            while (!queue.isEmpty()) {
                startTime = queue.remove();
                List<WorkCalendar> effectiveCalendars = getEffectiveCalendar(startTime, workCalendars);
                if (ObjectNull.isNull(effectiveCalendars)) {
                    continue;
                }
                // 获取使用的日历
                WorkCalendar useWorkCalendar = getUseCalendar(effectiveCalendars);

                // 计算最近的可开工时间
                if (ObjectNull.isNull(useWorkCalendar.getWorkModeTimes())) {
                    LocalDateTime nextTime = tomorrowAtMidnight(startTime);
                    queue.add(nextTime);
                } else {
                    LocalDateTime recentStartTime = findRecentWorkTime(startTime, useWorkCalendar);
                    if (ObjectNull.isNotNull(recentStartTime)) {
                        startTime = recentStartTime;
                    } else {
                        LocalDateTime nextTime = tomorrowAtMidnight(startTime);
                        queue.add(nextTime);
                    }
                }
            }
            return startTime;
        }
    }
}
