package cn.bctools.aps.solve.util;

import cn.bctools.aps.service.facade.impl.adjustment.AbstractAdjustTask;
import cn.bctools.aps.solve.model.ProductionTask;
import cn.bctools.aps.util.PlanUtils;
import cn.bctools.common.utils.ObjectNull;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

/**
 * @author jvs
 * 任务匹配工具类
 * <p>
 * 用于任务重排时，匹配已固定的任务
 */
public class ProductionTaskMatcherUtils {
    private ProductionTaskMatcherUtils() {
    }

    /**
     * 匹配已固定的任务
     * <p>
     * 同一个订单可以重复生成任务，若该订单没有已固定的任务，则不需要处理，否则需要匹配已固定的任务
     *
     * @param taskList       新的生产任务集合
     * @param pinnedTaskList 已固定的生产任务集合
     */
    public static void pinnedTaskHandler(List<ProductionTask> taskList, List<ProductionTask> pinnedTaskList) {
        if (ObjectNull.isNull(taskList, pinnedTaskList)) {
            return;
        }
        // 待加入任务集合的固定任务
        Set<ProductionTask> addPinnedTasks = new HashSet<>();
        // 计划数量为0的任务
        Set<ProductionTask> zeroQuantityTasks = new HashSet<>();
        // 匹配固定任务后，还有剩余计划数量的任务编码
        Set<String> remainingQuantityTaskCodes = new HashSet<>();

        // 匹配固定任务
        for (ProductionTask task : taskList) {
            // 获取匹配的固定任务
            List<ProductionTask> matcherTaskList = matchPinnedTask(task, pinnedTaskList);
            if (ObjectNull.isNull(matcherTaskList)) {
                continue;
            }
            // 匹配后处理
            processTask(task, taskList, matcherTaskList);
            // 记录待加入任务集合的固定任务
            addPinnedTasks.addAll(matcherTaskList);
            // 记录计划数量为0的任务
            if (task.getQuantity().compareTo(BigDecimal.ZERO) == 0) {
                zeroQuantityTasks.add(task);
            }
            // 新任务的计划数量分配给固定任务后还有剩余（重新排产时订单的需求数量有增加）
            if (task.getQuantity().compareTo(BigDecimal.ZERO) > 0) {
                boolean exists = matcherTaskList.stream()
                        .anyMatch(matcherTask -> task.getCode().equals(matcherTask.getCode()));
                if (exists) {
                    remainingQuantityTaskCodes.add(task.getCode());
                }
            }
        }

        // 处理匹配固定任务后，还有剩余计划数量的任务编码
        processRemainingQuantityTask(remainingQuantityTaskCodes, taskList);
        // 固定任务加入任务集合
        List<String> pinnedTasks = addPinnedTasks.stream().map(ProductionTask::getCode).toList();
        taskList.removeIf(task -> pinnedTasks.contains(task.getCode()));
        taskList.addAll(addPinnedTasks);
        // 移除计划数量为0的任务
        taskList.removeAll(zeroQuantityTasks);
    }

    /**
     * 匹配固定任务
     *
     * @param task           新任务
     * @param pinnedTaskList 固定任务集合
     * @return 新任务匹配的固定任务
     */
    private static List<ProductionTask> matchPinnedTask(ProductionTask task, List<ProductionTask> pinnedTaskList) {
        // 根据新任务编码匹配固定任务
        List<ProductionTask> matcherTaskList = pinnedTaskList.stream()
                .filter(pinnedTask -> task.getCode().equals(pinnedTask.getCode()) || task.getCode().equals(pinnedTask.getOriginTaskCode()))
                .collect(Collectors.toList());
        // 根据匹配的固定任务的合并任务编码，找到对应的合并任务，并加入匹配固定任务集合
        Set<String> mergeTaskCodes = matcherTaskList.stream()
                .map(ProductionTask::getMergeTaskCode)
                .filter(ObjectNull::isNotNull)
                .collect(Collectors.toSet());
        pinnedTaskList.stream()
                .filter(pinnedTask -> mergeTaskCodes.contains(pinnedTask.getCode()))
                .forEach(matcherTaskList::add);
        return matcherTaskList;
    }


    /**
     * 匹配后处理
     *
     * @param task                  匹配的新任务
     * @param taskList              新的生产任务集合
     * @param matcherPinnedTaskList 匹配的固定任务集合
     */
    private static void processTask(ProductionTask task, List<ProductionTask> taskList, List<ProductionTask> matcherPinnedTaskList) {
        if (ObjectNull.isNull(matcherPinnedTaskList)) {
            return;
        }
        // 找到新任务的前置任务
        List<ProductionTask> frontTasks = listFrontTask(task.getCode(), taskList);
        // 找到新任务的后置任务
        List<ProductionTask> nextTasks = listNextTask(task.getCode(), taskList);

        // 计划数量分配、修改任务关系
        matcherPinnedTaskList.forEach(pinnedTask -> {
            changeTaskRelationship(pinnedTask, frontTasks, nextTasks);
            calculateTaskQuantity(task, pinnedTask);
        });
    }

    /**
     * 获取指定任务的前置任务
     *
     * @param code 任务编码
     * @param taskList 任务集合
     * @return 前置任务
     */
    private static List<ProductionTask> listFrontTask(String code, Collection<ProductionTask> taskList) {
        return taskList.stream()
                .filter(t -> ObjectNull.isNotNull(t.getNextTaskCodes()) && t.getNextTaskCodes().contains(code))
                .collect(Collectors.toList());
    }

    /**
     * 获取指定任务的后置任务
     *
     * @param code 任务编码
     * @param taskList 任务集合
     * @return 后置任务
     */
    private static List<ProductionTask> listNextTask(String code, Collection<ProductionTask> taskList) {
        return taskList.stream()
                .filter(t -> ObjectNull.isNotNull(t.getFrontTaskCodes()) && t.getFrontTaskCodes().contains(code))
                .collect(Collectors.toList());
    }

    /**
     * 修改任务关系
     * <p>
     * 修改前置任务、后置任务
     *
     * @param pinnedTask 固定的任务
     * @param frontTasks 新任务的前置任务
     * @param nextTasks  新任务的后置任务
     */
    private static void changeTaskRelationship(ProductionTask pinnedTask, List<ProductionTask> frontTasks, List<ProductionTask> nextTasks) {
        // 合并任务的子任务应该移除
        if (ObjectNull.isNotNull(pinnedTask.getMergeTaskCode())) {
            frontTasks.forEach(task -> task.removeNextTask(pinnedTask.getCode()));
            nextTasks.forEach(task -> task.removeFrontTask(pinnedTask.getCode()));
        } else {
            frontTasks.forEach(task -> task.addNextTask(pinnedTask));
            nextTasks.forEach(task -> task.addFrontTask(pinnedTask));
        }

    }


    /**
     * 计算任务制造数量
     *
     * @param task 新任务
     * @param pinnedTask 固定任务
     */
    private static void calculateTaskQuantity(ProductionTask task, ProductionTask pinnedTask) {
        // 合并任务不参与计算
        if (pinnedTask.getMergeTask()) {
            return;
        }
        // 新任务的制造数量 - 固定任务的制造数量。
        BigDecimal deductQuantity = task.getQuantity().subtract(pinnedTask.getQuantity());
        if (deductQuantity.compareTo(BigDecimal.ZERO) > 0) {
            task.setQuantity(deductQuantity);
        } else {
            task.setQuantity(BigDecimal.ZERO);
        }
    }


    /**
     * 处理匹配固定任务后，还有剩余计划数量的任务
     *
     * @param remainingQuantityTaskCodes 匹配固定任务后，还有剩余计划数量的任务编码集合
     * @param taskList 所有新任务
     */
    private static void processRemainingQuantityTask(Set<String> remainingQuantityTaskCodes, List<ProductionTask> taskList) {
        // 生成新任务编码，替换原任务编码，替换前后置关系
        remainingQuantityTaskCodes.forEach(code -> {
            Optional<ProductionTask> optionalProductionTask = taskList.stream()
                    .filter(task -> code.equals(task.getCode()))
                    .findFirst();
            if (optionalProductionTask.isPresent()) {
                ProductionTask newTask = optionalProductionTask.get()
                        .setCode(PlanUtils.generateTaskCode(AbstractAdjustTask.SPLIT_TASK_CODE_PREFIX))
                        .setOriginTaskCode(code);
                // 修改前置任务的后置任务
                List<ProductionTask> frontTasks = listFrontTask(code, taskList);
                frontTasks.forEach(frontTask -> {
                    if (ObjectNull.isNotNull(frontTask.getNextTaskCodes())) {
                        frontTask.removeNextTask(code);
                        frontTask.addNextTask(newTask);
                    }
                });
                // 修改后置任务的前置任务
                List<ProductionTask> nextTasks = listNextTask(code, taskList);
                nextTasks.forEach(nextTask -> {
                    if (ObjectNull.isNotNull(nextTask.getFrontTasks())) {
                        nextTask.removeFrontTask(code);
                        nextTask.addFrontTask(newTask);
                    }
                });
            }
        });
    }
}
