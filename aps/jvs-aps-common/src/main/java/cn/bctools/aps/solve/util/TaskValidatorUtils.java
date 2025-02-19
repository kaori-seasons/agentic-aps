package cn.bctools.aps.solve.util;

import cn.bctools.aps.dto.schedule.TaskDTO;
import cn.bctools.aps.entity.dto.ProcessRouteNodePropertiesDTO;
import cn.bctools.aps.entity.enums.ProcessRelationshipEnum;
import cn.bctools.aps.util.DateUtils;
import cn.bctools.common.utils.ObjectNull;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.stream.Collectors;

/**
 * @author jvs
 * 任务校验工具
 */
@Slf4j
public class TaskValidatorUtils {
    private TaskValidatorUtils() {
    }

    /**
     * 校验任务是否可安排到指定资源
     *
     * @param resourceId  资源id
     * @param taskProcess 任务工序
     * @return true-任务可安排到该资源，false-任务不能安排到该资源
     */
    public static boolean matchTaskResource(String resourceId, ProcessRouteNodePropertiesDTO taskProcess) {
        return taskProcess.getUseMainResources().stream().anyMatch(resource -> resource.getId().equals(resourceId));
    }

    /**
     * 校验任务是否合规
     *
     * @param tasks 待校验的任务集合
     * @return 不合规的任务编码集合
     */
    public static Set<String> checkTaskCompliance(List<TaskDTO> tasks) {
        if (ObjectNull.isNull(tasks)) {
            return Collections.emptySet();
        }
        List<TaskDTO> taskList = tasks.stream().filter(task -> ObjectNull.isNotNull(task.getDiscard()) && !task.getDiscard()).toList();
        // 未成功计划的任务编码（没有计划主资源 或 没有计划开始时间 或 没有计划结束时间）
        Set<String> undistributedCodes = new HashSet<>();
        // 同资源计划时间交叉的任务编码
        Set<String> resourceTimeConflictCodes = new HashSet<>();
        // 关联任务存在时间冲突的任务编码
        Set<String> taskTimeConflictCodes = new HashSet<>();

        // 资源任务
        Map<String, List<TaskDTO>> resourceTaskMap = new HashMap<>();

        for (TaskDTO task : taskList) {
            // 校验任务是否安排了计划
            if (!validateAllocation(task)) {
                undistributedCodes.add(task.getCode());
                continue;
            }

            // 校验同资源是否存在计划时间交叉的任务
            Set<String> currentResourceTimeConflictCodes = validateResourceTimeConflicts(task, resourceTaskMap);
            if (ObjectNull.isNotNull(currentResourceTimeConflictCodes)) {
                resourceTimeConflictCodes.addAll(currentResourceTimeConflictCodes);
            }

            // 关联任务校验存在时间冲突的任务
            Set<String> currentTaskTimeConflictCodes = validateTaskTimeConflicts(task, taskList);
            if (ObjectNull.isNotNull(currentTaskTimeConflictCodes)) {
                taskTimeConflictCodes.addAll(currentTaskTimeConflictCodes);
            }
        }

        // 合并所有不合规的任务编码
        Set<String> noncompliance = new HashSet<>();
        if (ObjectNull.isNotNull(undistributedCodes)) {
            log.debug("未成功计划的任务编码：{}", undistributedCodes);
            noncompliance.addAll(undistributedCodes);
        }
        if (ObjectNull.isNotNull(resourceTimeConflictCodes)) {
            log.debug("同资源计划时间冲突的任务编码：{}", resourceTimeConflictCodes);
            noncompliance.addAll(resourceTimeConflictCodes);
        }
        if (ObjectNull.isNotNull(taskTimeConflictCodes)) {
            log.debug("关联任务计划时间冲突的任务编码：{}", taskTimeConflictCodes);
            noncompliance.addAll(taskTimeConflictCodes);
        }

        return noncompliance;
    }

    /**
     * 校验任务是否安排了计划
     *
     * @param task 校验任务
     * @return true-已安排，false-未安排
     */
    private static boolean validateAllocation(TaskDTO task) {
        // 校验是否有计划主资源、计划开始时间、计划结束时间。 只要有一个为空，则表示任务安排失败
        return ObjectNull.isNotNull(task.getMainResourceId())
                && ObjectNull.isNotNull(task.getStartTime())
                && ObjectNull.isNotNull(task.getEndTime());
    }

    /**
     * 校验同一资源中任务的时间冲突
     *
     * @param task            校验任务
     * @param resourceTaskMap 资源任务
     * @return true-时间不冲突，false-时间冲突
     */
    private static Set<String> validateResourceTimeConflicts(TaskDTO task, Map<String, List<TaskDTO>> resourceTaskMap) {
        // 不校验合并任务的子任务
        if (ObjectNull.isNotNull(task.getMergeTaskCode())) {
            return Collections.emptySet();
        }

        // 筛选出计划时间有交集的任务
        List<TaskDTO> resourceTasks = Optional.ofNullable(resourceTaskMap.get(task.getMainResourceId())).orElseGet(ArrayList::new);
        Set<String> timeConflictCodes = resourceTasks.stream()
                .filter(resourceTask -> DateUtils.hasOverlap(false,
                        task.getStartTime(), task.getEndTime(),
                        resourceTask.getStartTime(), resourceTask.getEndTime()))
                .map(TaskDTO::getCode)
                .collect(Collectors.toSet());
        if (ObjectNull.isNotNull(timeConflictCodes)) {
            timeConflictCodes.add(task.getCode());
        }

        // 将当前任务加入资源任务
        if (ObjectNull.isNull(resourceTasks)) {
            resourceTaskMap.put(task.getMainResourceId(), resourceTasks);
        }
        resourceTasks.add(task);

        return timeConflictCodes;
    }


    /**
     * 校验关联任务的的时间冲突
     *
     * @param task  校验任务
     * @param tasks 所有任务
     * @return 时间冲突的任务编码
     */
    public static Set<String> validateTaskTimeConflicts(TaskDTO task, List<TaskDTO> tasks) {
        // 不直接校验合并任务
        if (task.getMergeTask()) {
            return Collections.emptySet();
        }

        // 没有前置任务，直接跳过
        if (ObjectNull.isNull(task.getFrontTaskCodes())) {
            return Collections.emptySet();
        }

        // 查找当前任务的前置任务
        List<TaskDTO> frontTasks = tasks.stream()
                .filter(t -> task.getFrontTaskCodes().contains(t.getCode()))
                .toList();

        // 根据不同的工序关系校验
        List<TaskDTO> taskTimeConflictTasks = new ArrayList<>();
        // 若是工序任务链的首道任务，则与前置任务的关系一定是ES的
        // ES关系
        if (task.getStartTask() || ProcessRelationshipEnum.ES.equals(task.getProcessInfo().getProcessRelationship())) {
            taskTimeConflictTasks = frontTasks.stream()
                    .filter(t -> t.getEndTime().isAfter(task.getStartTime()))
                    .collect(Collectors.toList());
        }

        // EE关系
        if (ProcessRelationshipEnum.EE.equals(task.getProcessInfo().getProcessRelationship())) {
            boolean conflict = frontTasks.stream()
                    .map(TaskDTO::getStartTime)
                    .min(Comparator.naturalOrder())
                    .stream()
                    .anyMatch(minStartTime -> minStartTime.isAfter(task.getStartTime()));
            if (conflict) {
                // 所有前置任务都冲突
                taskTimeConflictTasks = frontTasks;
            } else {
                // 前置任务的结束时间 < 当前任务的结束时间，则时间冲突
                taskTimeConflictTasks = frontTasks.stream()
                        .filter(t -> t.getEndTime().isAfter(task.getEndTime()))
                        .collect(Collectors.toList());
            }
        }

        // 若存在时间冲突的任务, 将冲突的任务编码加入集合
        Set<String> taskTimeConflictCodes = new HashSet<>();
        if (ObjectNull.isNotNull(taskTimeConflictTasks)) {
            // 当前任务与前置任务有时间冲突，则将当前任务加入冲突集合
            taskTimeConflictTasks.add(task);
            // 得到冲突的任务编码集合
            taskTimeConflictCodes = taskTimeConflictTasks.stream()
                    .map(TaskDTO::getCode)
                    .collect(Collectors.toSet());

            // 若有合并任务的子任务，则找到合并任务，并加入时间冲突任务编码集合
            Set<String> mergeTaskCodes = taskTimeConflictTasks.stream()
                    .map(TaskDTO::getMergeTaskCode)
                    .filter(ObjectNull::isNotNull)
                    .collect(Collectors.toSet());
            tasks.stream()
                    .map(TaskDTO::getCode)
                    .filter(mergeTaskCodes::contains)
                    .forEach(taskTimeConflictCodes::add);
        }
        return taskTimeConflictCodes;
    }

}
