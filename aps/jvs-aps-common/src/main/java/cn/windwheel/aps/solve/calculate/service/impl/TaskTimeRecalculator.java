package cn.windwheel.aps.solve.calculate.service.impl;

import cn.windwheel.aps.solve.calculate.TaskTimeDirector;
import cn.windwheel.aps.solve.model.*;
import cn.windwheel.aps.solve.score.SchedulingRules;
import java.time.LocalDateTime;
import java.util.*;

/**
 * 每次列表变动后，从当前队列和工艺图确定性推导时间。
 * 所有活动时间先清空，避免试探移动撤销时遗留来自其他候选解的开始时间。
 * 环形队列依赖保持未分配时间，由硬约束拒绝该候选。
 */
final class TaskTimeRecalculator {
    private TaskTimeRecalculator() {}

    static void recalculate(TaskTimeDirector director, TaskTimeForwardServiceImpl forward, TaskTimeBackwardServiceImpl backward) {
        SchedulingSolution solution = director.getWorkingSolution();
        Map<String,ProductionTask> tasks = new TreeMap<>();
        Map<String,Set<String>> next = new HashMap<>();
        Map<String,Integer> degree = new HashMap<>();
        Map<String,MainProductionResource> assignments = new HashMap<>();
        Map<String,ProductionTask> previous = new HashMap<>();
        Map<String,ProductionTask> following = new HashMap<>();
        for (ProductionTask task : solution.getTasks()) {
            if (!SchedulingRules.active(task)) { continue; }
            tasks.put(task.getCode(),task);
            next.put(task.getCode(),new TreeSet<>());
            degree.put(task.getCode(),0);
            if (!Boolean.TRUE.equals(task.getPinned())) {
                change(director,task,null);
            }
        }
        for (MainProductionResource resource : solution.getMainResources()) {
            ProductionTask prior = null;
            for (ProductionTask task : resource.getTaskList()) {
                if (!SchedulingRules.active(task)) { continue; }
                assignments.put(task.getCode(),resource);
                if (Boolean.TRUE.equals(task.getPinned())) { continue; }
                if (prior != null) {
                    previous.put(task.getCode(),prior);
                    following.put(prior.getCode(),task);
                    edge(prior.getCode(),task.getCode(),next,degree,backward != null);
                }
                prior = task;
            }
        }
        for (ProductionTask task : tasks.values()) {
            if (task.getFrontTaskCodes() == null) { continue; }
            for (String front : task.getFrontTaskCodes()) {
                if (tasks.containsKey(front)) {
                    edge(front,task.getCode(),next,degree,backward != null);
                }
            }
        }
        PriorityQueue<String> ready = new PriorityQueue<>();
        degree.forEach((code,count) -> { if (count == 0) { ready.add(code); } });
        while (!ready.isEmpty()) {
            String code = ready.remove();
            ProductionTask task = tasks.get(code);
            MainProductionResource resource = assignments.get(code);
            if (resource != null && !Boolean.TRUE.equals(task.getPinned())) {
                LocalDateTime start;
                if (forward != null) {
                    ProductionTask prior = previous.get(code);
                    LocalDateTime available = prior == null ? solution.getPlanTime() : prior.getEndTime();
                    start = available == null ? null : forward.calculateStartTime(resource,task,prior,available,
                            solution.getTasks(),resource.getTaskList().stream()
                                    .takeWhile(value -> value != task)
                                    .filter(value -> !Boolean.TRUE.equals(value.getPinned())).toList());
                } else {
                    ProductionTask after = following.get(code);
                    LocalDateTime available = after == null ? solution.getPlanTime() : after.getStartTime();
                    start = available == null ? null : backward.calculateStartTime(resource,task,available,solution.getTasks());
                }
                change(director,task,start);
            }
            for (String successor : next.get(code)) {
                int remaining = degree.compute(successor,(key,count) -> count - 1);
                if (remaining == 0) { ready.add(successor); }
            }
        }
    }

    private static void edge(String from, String to, Map<String,Set<String>> next,
                             Map<String,Integer> degree, boolean reverse) {
        String source = reverse ? to : from;
        String target = reverse ? from : to;
        if (next.get(source).add(target)) { degree.compute(target,(key,count) -> count + 1); }
    }

    private static void change(TaskTimeDirector director, ProductionTask task, LocalDateTime start) {
        if (Objects.equals(task.getStartTime(),start)) { return; }
        director.beforeVariableChanged(task,"startTime");
        task.setStartTime(start);
        director.afterVariableChanged(task,"startTime");
    }
}
