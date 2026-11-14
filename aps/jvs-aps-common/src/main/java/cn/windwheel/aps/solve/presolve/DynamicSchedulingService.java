package cn.windwheel.aps.solve.presolve;

import cn.windwheel.aps.entity.*;
import cn.windwheel.aps.entity.enums.*;
import cn.windwheel.aps.solve.model.*;
import cn.windwheel.aps.solve.score.SchedulingRules;
import cn.windwheel.common.exception.BusinessException;
import cn.windwheel.common.utils.BeanCopyUtil;
import org.springframework.stereotype.Component;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/** 只接受明确提交的结构化增量，模型建议经调用方确认后才会进入此入口。 */
@Component
public class DynamicSchedulingService {
    public void prepare(BasicData data, PlanningStrategy strategy, DynamicSchedulingRequest request) {
        if (request == null) { return; }
        if (strategy.getConfig().getSolverEngine() != SolverEngineEnum.TIMEFOLD) {
            throw new BusinessException("动态局部重排需要选择TIMEFOLD引擎");
        }
        if (request.getFreezeUntil() == null || request.getEvents() == null || request.getEvents().isEmpty()
                || request.getEvents().size() > 100 || request.getSeeds() == null || request.getSeeds().size() > 1000) {
            throw new BusinessException("动态事件、冻结时间或种子无效");
        }
        Set<String> affectedOrders = new HashSet<>();
        Set<String> affectedResources = new HashSet<>();
        Set<String> delayedMaterials = new HashSet<>();
        Set<String> events = new HashSet<>();
        for (var event : request.getEvents()) {
            if (event == null || event.getType() == null || event.getTargetId() == null
                    || !events.add(event.getType() + ":" + event.getTargetId())) {
                throw new BusinessException("动态事件缺少标识或重复");
            }
            boolean unrelatedDates = switch (event.getType()) {
                case INSERT_ORDER -> event.getStart() != null || event.getEnd() != null
                        || event.getAvailableAt() != null || event.getDeliveryTime() != null;
                case RESOURCE_UNAVAILABLE -> event.getAvailableAt() != null || event.getDeliveryTime() != null;
                case MATERIAL_DELAY -> event.getStart() != null || event.getEnd() != null || event.getDeliveryTime() != null;
                case DUE_DATE_CHANGE -> event.getStart() != null || event.getEnd() != null || event.getAvailableAt() != null;
            };
            if (unrelatedDates) { throw new BusinessException("事件类型与时间字段不匹配，请核对事件"); }
            switch (event.getType()) {
                case INSERT_ORDER, DUE_DATE_CHANGE -> {
                    ProductionOrder order = data.getProductionOrders().stream()
                            .filter(item -> !Boolean.TRUE.equals(item.getSupplement()) && event.getTargetId().equals(item.getId())).findFirst()
                            .orElseThrow(() -> new BusinessException("事件引用的订单不在当前租户可排池"));
                    affectedOrders.add(order.getId());
                    if (event.getType() == DynamicSchedulingRequest.EventType.DUE_DATE_CHANGE) {
                        if (event.getDeliveryTime() == null) { throw new BusinessException("交期变更缺少新交期"); }
                        order.setDeliveryTime(event.getDeliveryTime());
                    } else {
                        order.setPriority(Integer.MAX_VALUE);
                    }
                }
                case RESOURCE_UNAVAILABLE -> {
                    if (data.getProductionResources().stream().noneMatch(r -> event.getTargetId().equals(r.getId()))
                            || event.getStart() == null || event.getEnd() == null || !event.getStart().isBefore(event.getEnd())) {
                        throw new BusinessException("设备不可用时间窗无效");
                    }
                    affectedResources.add(event.getTargetId());
                }
                case MATERIAL_DELAY -> {
                    if (!data.getMaterialMap().containsKey(event.getTargetId()) || event.getAvailableAt() == null) {
                        throw new BusinessException("物料延迟事件无效");
                    }
                    delayedMaterials.add(event.getTargetId());
                }
            }
        }
        List<PlanTaskAdjustPO> baseline = data.getBaselineTasks();
        // 先包含直接关联订单，再包含它们共用资源上的任务。这样保留局部范围，又不会禁止急单使用已有资源。
        for (PlanTaskAdjustPO task : baseline) {
            if (affectedOrders.contains(task.getMainOrderId()) || affectedResources.contains(task.getMainResourceId())
                    || task.getInputMaterials() != null && task.getInputMaterials().stream()
                        .anyMatch(material -> delayedMaterials.contains(material.getId()))) {
                affectedOrders.add(task.getMainOrderId());
            }
        }
        Set<String> sharedResources = baseline.stream().filter(task -> affectedOrders.contains(task.getMainOrderId()))
                .map(PlanTaskAdjustPO::getMainResourceId).collect(Collectors.toSet());
        // 新插单尚无历史任务，以其工艺候选资源扩充影响面。
        Set<String> materials = new HashSet<>();
        java.util.ArrayDeque<String> queue = new java.util.ArrayDeque<>();
        data.getProductionOrders().stream().filter(order -> affectedOrders.contains(order.getId()))
                .map(ProductionOrder::getMaterialId).filter(Objects::nonNull).forEach(queue::add);
        while (!queue.isEmpty()) {
            String material = queue.remove();
            if (!materials.add(material)) { continue; }
            var route = data.getProcessRouteMap().get(material);
            if (route != null) {
                route.getNodes().stream().map(node -> node.getData()).filter(Objects::nonNull)
                        .filter(process -> process.getUseMainResources() != null)
                        .forEach(process -> process.getUseMainResources().forEach(resource -> sharedResources.add(resource.getId())));
            }
            data.getBomMap().getOrDefault(material,List.of()).forEach(child -> queue.add(child.getMaterialId()));
        }
        Set<String> movableOrders = baseline.stream().filter(task -> sharedResources.contains(task.getMainResourceId()))
                .map(PlanTaskAdjustPO::getMainOrderId).collect(Collectors.toSet());
        movableOrders.addAll(affectedOrders);
        Map<String,PlanTaskAdjustPO> pinned = data.getPinnedTasks().stream()
                .collect(Collectors.toMap(PlanTaskAdjustPO::getCode, task -> task, (a,b) -> a, LinkedHashMap::new));
        for (PlanTaskAdjustPO task : baseline) {
            boolean inProgress = task.getTaskStatus() == PlanTaskStatusEnum.PARTIALLY_COMPLETED
                    || task.getTaskStatus() == PlanTaskStatusEnum.COMPLETED;
            if (inProgress || Boolean.TRUE.equals(task.getPinned()) || task.getStartTime().isBefore(request.getFreezeUntil())
                    || !movableOrders.contains(task.getMainOrderId())) {
                pinned.putIfAbsent(task.getCode(), BeanCopyUtil.copy(task, PlanTaskAdjustPO.class).setPinned(true));
            }
        }
        data.setPinnedTasks(new ArrayList<>(pinned.values()));
        data.setDynamicRequest(request);
        if (request.getRecommendedProfile() != null) {
            strategy.getConfig().setSolverProfile(request.getRecommendedProfile());
        }
        if (request.getRecommendedWeights() != null) {
            strategy.getConfig().setConstraintWeightConfig(request.getRecommendedWeights());
        }
        cn.windwheel.aps.solve.config.SolveManageConfigurator.validate(strategy.getConfig());
    }

    public void apply(SchedulingSolution solution, BasicData data) {
        var request = data.getDynamicRequest();
        Set<String> taskCodes = solution.getTasks().stream().map(ProductionTask::getCode).collect(Collectors.toSet());
        for (ProductionTask task : solution.getTasks()) {
            if (task.getFrontTaskCodes() != null && !taskCodes.containsAll(task.getFrontTaskCodes())) {
                Set<String> missing = new TreeSet<>(task.getFrontTaskCodes());
                missing.removeAll(taskCodes);
                throw new BusinessException("任务" + task.getCode() + "缺少前置任务：" + String.join(",",missing));
            }
        }
        for (ProductionTask task : solution.getTasks()) {
            task.setWeights(solution.getStrategy().getConfig().getConstraintWeightConfig());
            if (Boolean.TRUE.equals(task.getPinned())) {
                if (!SchedulingRules.scheduled(task)) { throw new BusinessException("冻结任务缺少资源或时间"); }
                task.setFrozenResourceId(task.getResource().getId()).setFrozenStartTime(task.getStartTime()).setFrozenEndTime(task.getEndTime());
            }
        }
        if (request != null) {
            for (var event : request.getEvents()) {
                if (event.getType() == DynamicSchedulingRequest.EventType.RESOURCE_UNAVAILABLE) {
                    solution.getMainResources().stream().filter(r -> r.getId().equals(event.getTargetId()))
                            .findFirst().orElseThrow(() -> new BusinessException("不可用资源未启用"))
                            .getDowntimes().add(new ResourceDowntime().setResourceId(event.getTargetId())
                                    .setStart(event.getStart()).setEnd(event.getEnd()));
                }
                if (event.getType() == DynamicSchedulingRequest.EventType.MATERIAL_DELAY) {
                    solution.getTasks().stream().filter(task -> task.getInputMaterials() != null
                            && task.getInputMaterials().stream().anyMatch(m -> event.getTargetId().equals(m.getId())))
                            .forEach(task -> task.setNotBefore(event.getAvailableAt()));
                }
            }
        }
        if (solution.getStrategy().getConfig().getSolverEngine() == SolverEngineEnum.TIMEFOLD) {
            for (MainProductionResource resource : solution.getMainResources()) {
                List<ProductionTask> pinned = solution.getTasks().stream()
                        .filter(task -> SchedulingRules.active(task) && Boolean.TRUE.equals(task.getPinned())
                                && task.getResource() != null && resource.getId().equals(task.getResource().getId()))
                        .sorted(Comparator.comparing(ProductionTask::getStartTime)).collect(Collectors.toCollection(ArrayList::new));
                resource.setTaskList(pinned).setFirstUnpinnedIndex(pinned.size());
            }
        } else {
            // OptaPlanner 9的值域不含冻结任务；将其留在规划列表会把初始化分数错误加为正数。
            // 冻结任务仍作为问题事实参与时间推导、依赖与资源冲突检查。
            solution.getMainResources().forEach(resource ->
                    resource.getTaskList().removeIf(task -> Boolean.TRUE.equals(task.getPinned())));
        }
        if (request == null) { return; }
        Map<String,ProductionTask> byCode = solution.getTasks().stream()
                .collect(Collectors.toMap(ProductionTask::getCode, task -> task));
        for (var old : data.getBaselineTasks().stream().sorted(Comparator.comparing(PlanTaskAdjustPO::getStartTime)).toList()) {
            ProductionTask task = byCode.get(old.getCode());
            if (task == null || Boolean.TRUE.equals(task.getPinned()) || !SchedulingRules.active(task)) { continue; }
            solution.getMainResources().stream().filter(resource -> resource.getId().equals(old.getMainResourceId()))
                    .filter(resource -> task.getProcess().getUseMainResources().stream().anyMatch(r -> r.getId().equals(resource.getId())))
                    .findFirst().ifPresent(resource -> resource.getTaskList().add(task));
        }
        Set<String> seeded = new HashSet<>();
        for (var seed : request.getSeeds()) {
            if (seed == null || !seeded.add(seed.getTaskCode())) { throw new BusinessException("初始解种子重复"); }
            ProductionTask task = solution.getTasks().stream().filter(t -> t.getCode().equals(seed.getTaskCode()))
                    .findFirst().orElseThrow(() -> new BusinessException("初始解引用未知任务"));
            MainProductionResource resource = solution.getMainResources().stream().filter(r -> r.getId().equals(seed.getResourceId()))
                    .findFirst().orElseThrow(() -> new BusinessException("初始解引用未知资源"));
            if (!SchedulingRules.active(task) || Boolean.TRUE.equals(task.getPinned()) || task.getProcess().getUseMainResources().stream()
                    .noneMatch(r -> r.getId().equals(resource.getId()))) {
                throw new BusinessException("初始解试图移动冻结任务或使用不匹配的资源");
            }
            solution.getMainResources().forEach(candidate -> candidate.getTaskList().remove(task));
            resource.getTaskList().add(Math.min(resource.getTaskList().size(),
                    Math.max(resource.getFirstUnpinnedIndex(), seed.getPosition())), task);
        }
    }
}
