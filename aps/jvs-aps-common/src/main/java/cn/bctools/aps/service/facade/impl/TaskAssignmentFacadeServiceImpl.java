package cn.bctools.aps.service.facade.impl;

import cn.bctools.aps.annotation.ThroughputFormatValidator;
import cn.bctools.aps.dto.schedule.WorkAssignmentDTO;
import cn.bctools.aps.entity.PlanTaskOrderPO;
import cn.bctools.aps.entity.PlanTaskPO;
import cn.bctools.aps.entity.ProductionResourcePO;
import cn.bctools.aps.entity.dto.ProcessUseMainResourcesDTO;
import cn.bctools.aps.entity.enums.PlanTaskStatusEnum;
import cn.bctools.aps.entity.enums.PlanningDirectionEnum;
import cn.bctools.aps.enums.DurationFormatTypeEnum;
import cn.bctools.aps.service.PlanInfoService;
import cn.bctools.aps.service.PlanTaskOrderService;
import cn.bctools.aps.service.PlanTaskService;
import cn.bctools.aps.service.ProductionResourceService;
import cn.bctools.aps.service.facade.TaskAssignmentFacadeService;
import cn.bctools.aps.service.facade.WorkCalendarFacadeService;
import cn.bctools.aps.service.facade.param.GenerateTaskAssignParam;
import cn.bctools.aps.solve.model.WorkCalendar;
import cn.bctools.aps.solve.util.DurationUtils;
import cn.bctools.aps.solve.util.TaskCalendarUtils;
import cn.bctools.aps.solve.util.TaskDurationUtils;
import cn.bctools.aps.util.BigDecimalUtils;
import cn.bctools.aps.vo.schedule.WorkAssignmentVO;
import cn.bctools.common.exception.BusinessException;
import cn.bctools.common.utils.ObjectNull;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.stream.Collectors;

/**
 * @author jvs
 */
@Service
@AllArgsConstructor
public class TaskAssignmentFacadeServiceImpl implements TaskAssignmentFacadeService {

    private final PlanTaskService planTaskService;
    private final PlanTaskOrderService planTaskOrderService;
    private final WorkCalendarFacadeService workCalendarFacadeService;
    private final ProductionResourceService productionResourceService;
    private final PlanInfoService planInfoService;

    @Override
    public List<WorkAssignmentVO> workAssign(WorkAssignmentDTO workAssignment) {
        if (!workAssignment.getBeginTime().isBefore(workAssignment.getEndTime())) {
            throw new BusinessException("起始时间必须在结束时间之前");
        }

        // 查询指定时间区间内未完成的计划任务
        List<PlanTaskPO> planTasks = planTaskService.list(Wrappers.<PlanTaskPO>lambdaQuery()
                .ne(PlanTaskPO::getTaskStatus, PlanTaskStatusEnum.COMPLETED)
                .le(PlanTaskPO::getStartTime, workAssignment.getEndTime())
                .ge(PlanTaskPO::getEndTime, workAssignment.getBeginTime()));
        if (ObjectNull.isNull(planTasks)) {
            return Collections.emptyList();
        }

        // 按资源分组任务
        Map<String, List<PlanTaskPO>> resourceTaskMap = planTasks.stream()
                .collect(Collectors.groupingBy(PlanTaskPO::getMainResourceId));

        // 查询资源
        Map<String, ProductionResourcePO> productionResourceMap = productionResourceService.listByIds(resourceTaskMap.keySet())
                .stream()
                .collect(Collectors.toMap(ProductionResourcePO::getId, Function.identity()));

        // 查询资源可用日历
        Map<String, List<WorkCalendar>> workCalendarMap = workCalendarFacadeService.listResourceScheduleCalendar(resourceTaskMap.keySet());

        // 查询任务订单信息
        Set<String> orderIds = planTasks.stream()
                .map(PlanTaskPO::getProductionOrderId)
                .filter(ObjectNull::isNotNull)
                .collect(Collectors.toSet());
        Map<String, PlanTaskOrderPO> planTaskOrderMap = planTaskOrderService.listByOrderIds(orderIds)
                .stream()
                .collect(Collectors.toMap(PlanTaskOrderPO::getOrderId, Function.identity()));

        // 修改任务计划最近派工时间
        planInfoService.updateAssignmentTime(workAssignment.getEndTime());

        // 遍历资源计划任务，生成派工
        return resourceTaskMap.entrySet()
                .stream()
                .map(e -> {
                    String resourceId = e.getKey();
                    ProductionResourcePO resource = productionResourceMap.get(resourceId);
                    List<WorkCalendar> workCalendars = workCalendarMap.get(resourceId);
                    return resourceTaskAssign(workAssignment, resource, workCalendars, e.getValue(), planTaskOrderMap);
                })
                .flatMap(Collection::stream)
                .collect(Collectors.toList());
    }

    /**
     * 资源任务派工
     *
     * @param workAssignment        派工参数
     * @param resource              当前资源
     * @param resourceWorkCalendars 当前资源可用的工作日历
     * @param resourceTasks         当前资源任务
     * @param planTaskOrderMap      所有任务订单 Map<订单id, 订单信息>
     * @return 资源派工
     */
    private List<WorkAssignmentVO> resourceTaskAssign(WorkAssignmentDTO workAssignment,
                                                      ProductionResourcePO resource,
                                                      List<WorkCalendar> resourceWorkCalendars,
                                                      List<PlanTaskPO> resourceTasks,
                                                      Map<String, PlanTaskOrderPO> planTaskOrderMap) {
        List<WorkAssignmentVO> resourceTaskWorkAssignmentList = new ArrayList<>();

        // 待派工的任务
        List<PlanTaskPO> tasks = resourceTasks.stream()
                .filter(task -> ObjectNull.isNull(task.getMergeTaskCode()))
                .sorted(Comparator.comparing(PlanTaskPO::getStartTime))
                .toList();

        // 同资源任务逐个生成派工
        LocalDateTime earliestStartTime = workAssignment.getBeginTime();
        for (PlanTaskPO task : tasks) {
            if (task.getStartTime().isAfter(earliestStartTime)) {
                earliestStartTime = task.getStartTime();
            }
            List<WorkAssignmentVO> currentTaskWorkAssignmentList = singleTaskAssign(earliestStartTime, workAssignment.getEndTime(), task, resourceWorkCalendars, resource, resourceTasks, planTaskOrderMap);
            resourceTaskWorkAssignmentList.addAll(currentTaskWorkAssignmentList);
            Optional<LocalDateTime> optionalMaxEndTime = currentTaskWorkAssignmentList.stream()
                    .map(WorkAssignmentVO::getPlanEndTime)
                    .max(Comparator.naturalOrder());
            if (optionalMaxEndTime.isPresent()) {
                LocalDateTime planEndTime = optionalMaxEndTime.get();
                earliestStartTime = planEndTime;
                if (planEndTime.isEqual(workAssignment.getEndTime()) || planEndTime.isAfter(workAssignment.getEndTime())) {
                    break;
                }
            } else {
                break;
            }
        }

        // 按开始时间顺序排序
        return resourceTaskWorkAssignmentList;
    }

    /**
     * 单个任务派工
     *
     * @param earliestStartTime     本次派工任务计划起始时间
     * @param workAssignmentEndTime 派工任务区间结束时间
     * @param task                  待派工任务
     * @param resourceWorkCalendars 当前资源可用的工作日历
     * @param resource              当前资源
     * @param resourceTasks         当前资源任务
     * @param planTaskOrderMap      所有任务订单 Map<订单id, 订单信息>
     * @return 资源派工
     */
    private List<WorkAssignmentVO> singleTaskAssign(LocalDateTime earliestStartTime,
                                                    LocalDateTime workAssignmentEndTime,
                                                    PlanTaskPO task,
                                                    List<WorkCalendar> resourceWorkCalendars,
                                                    ProductionResourcePO resource,
                                                    List<PlanTaskPO> resourceTasks,
                                                    Map<String, PlanTaskOrderPO> planTaskOrderMap) {
        List<WorkAssignmentVO> currentTaskWorkAssignmentList = new ArrayList<>();

        // 获取任务工序下当前资源的产能
        Optional<ProcessUseMainResourcesDTO> optionalProcessResource = task.getProcessInfo().getUseMainResources().stream()
                .filter(useResource -> useResource.getId().equals(resource.getId()))
                .findFirst();
        String throughput = null;
        if (optionalProcessResource.isPresent()) {
            throughput = optionalProcessResource.get().getThroughput();
        } else {
            return currentTaskWorkAssignmentList;
        }
        Matcher matcherBatchThroughput = ThroughputFormatValidator.HOW_LONG_CAN_A_BATCH_BE_PROCESSED.matcher(throughput);
        if (matcherBatchThroughput.matches()) {
            GenerateTaskAssignParam param = new GenerateTaskAssignParam()
                    .setBatchThroughput(true)
                    .setTask(task)
                    .setPlanStartTime(task.getStartTime())
                    .setPlanEndTime(task.getEndTime())
                    .setPlanQuantity(task.getScheduledQuantity())
                    .setResource(resource)
                    .setResourceTasks(resourceTasks)
                    .setPlanTaskOrderMap(planTaskOrderMap);
            currentTaskWorkAssignmentList.addAll(generateTaskAssign(param));
            return currentTaskWorkAssignmentList;
        }


        // 已完成数量
        BigDecimal quantityCompleted = Optional.ofNullable(task.getQuantityCompleted()).orElseGet(() -> BigDecimal.ZERO);
        // 剩余待派工的数量
        BigDecimal quantity = task.getScheduledQuantity().subtract(quantityCompleted);
        if (quantity.compareTo(BigDecimal.ZERO) <= 0) {
            return currentTaskWorkAssignmentList;
        }

        // 从时间区间开始，根据排程计划生成派工计划
        // 派工计划开始时间
        LocalDateTime planStartTime = null;
        // 派工计划结束时间
        LocalDateTime planEndTime = null;
        // 派工计划数量
        BigDecimal planQuantity = null;

        // 计算派工计划开始时间
        planStartTime = TaskCalendarUtils.calculateStartTime(earliestStartTime, resourceWorkCalendars, PlanningDirectionEnum.FORWARD);
        // 计算任务最晚结束时间
        Duration taskDuration = TaskDurationUtils.calculateTaskDuration(resource.getId(), task.getProcessInfo(), quantity);
        LocalDateTime latestEndTime = TaskCalendarUtils.calculateEndTime(planStartTime, taskDuration, resourceWorkCalendars);
        // 如果任务最晚结束时间 在 派工区间结束时间之前或相等, 表示当前任务在派工区间工作时间内，全部派工
        if (latestEndTime.isBefore(workAssignmentEndTime) || latestEndTime.isEqual(workAssignmentEndTime)) {
            planEndTime = latestEndTime;
            planQuantity = quantity;
        } else {
            Duration remainingWorking = TaskCalendarUtils.workDuration(planStartTime, workAssignmentEndTime, resourceWorkCalendars);
            BigDecimal remainingMaxQuantity = TaskDurationUtils.calculateTotalOutput(throughput, remainingWorking)
                    .setScale(0, RoundingMode.CEILING);
            planQuantity = quantity.compareTo(remainingMaxQuantity) <= 0 ? quantity : remainingMaxQuantity;
            taskDuration = TaskDurationUtils.calculateTaskDuration(resource.getId(), task.getProcessInfo(), planQuantity);
            planEndTime = TaskCalendarUtils.calculateEndTime(planStartTime, taskDuration, resourceWorkCalendars);
        }
        // 生成派工计划
        GenerateTaskAssignParam param = new GenerateTaskAssignParam()
                .setBatchThroughput(false)
                .setTask(task)
                .setPlanStartTime(planStartTime)
                .setPlanEndTime(planEndTime)
                .setPlanQuantity(planQuantity)
                .setResource(resource)
                .setResourceTasks(resourceTasks)
                .setPlanTaskOrderMap(planTaskOrderMap);
        currentTaskWorkAssignmentList.addAll(generateTaskAssign(param));
        return currentTaskWorkAssignmentList;

    }

    /**
     * 生成任务的派工计划
     *
     * @return 任务的派工计划集合
     */
    private List<WorkAssignmentVO> generateTaskAssign(GenerateTaskAssignParam param) {
        // 为合并任务生成派工计划
        if (param.getTask().getMergeTask()) {
            return generateMergeTaskAssign(param);
        }

        // 为非合并任务生成派工计划
        return Collections.singletonList(createWorkAssign(param.getPlanStartTime(), param.getPlanEndTime(), param.getPlanQuantity(), param.getTask(), param.getResource(), param.getPlanTaskOrderMap()));
    }

    /**
     * 为合并任务生成派工计划
     *
     * @param param 参数
     * @return 合并任务生成派工计划
     */
    private List<WorkAssignmentVO> generateMergeTaskAssign(GenerateTaskAssignParam param) {
        boolean batchThroughput = param.getBatchThroughput();
        PlanTaskPO task = param.getTask();
        LocalDateTime planStartTime = param.getPlanStartTime();
        LocalDateTime planEndTime = param.getPlanEndTime();
        BigDecimal planQuantity = param.getPlanQuantity();
        ProductionResourcePO resource = param.getResource();
        List<PlanTaskPO> resourceTasks = param.getResourceTasks();
        Map<String, PlanTaskOrderPO> planTaskOrderMap = param.getPlanTaskOrderMap();

        // 获取合并任务未完成的子任务
        List<PlanTaskPO> childTasks = resourceTasks.stream()
                .filter(t -> task.getCode().equals(t.getMergeTaskCode()))
                .filter(t -> !PlanTaskStatusEnum.COMPLETED.equals(t.getTaskStatus()))
                .collect(Collectors.toList());

        if (batchThroughput) {
            return childTasks.stream()
                    .map(childTask -> {
                        BigDecimal childPlanQuantity = childTask.getScheduledQuantity().subtract(Optional.ofNullable(childTask.getQuantityCompleted()).orElseGet(() -> BigDecimal.ZERO));
                        return createWorkAssign(planStartTime, planEndTime, childPlanQuantity, task, resource, planTaskOrderMap);
                    })
                    .collect(Collectors.toList());
        }

        // 将派工数量按比例分配给各个子任务
        List<WorkAssignmentVO> workAssignmentList = new ArrayList<>();
        BigDecimal totalQuantity = childTasks.stream()
                .map(childTask -> childTask.getScheduledQuantity().subtract(Optional.ofNullable(childTask.getQuantityCompleted()).orElseGet(() -> BigDecimal.ZERO)))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal assignedQuantity = new BigDecimal(BigInteger.ZERO);
        childTasks.sort(Comparator.comparing(PlanTaskPO::getScheduledQuantity).reversed());
        for (PlanTaskPO childTask : childTasks) {
            if (assignedQuantity.compareTo(planQuantity) >= 0) {
                continue;
            }
            BigDecimal quantity = childTask.getScheduledQuantity().subtract(Optional.ofNullable(childTask.getQuantityCompleted()).orElseGet(() -> BigDecimal.ZERO));
            BigDecimal ratio = quantity.divide(totalQuantity, 2, RoundingMode.HALF_UP);
            BigDecimal childPlanQuantity = planQuantity.multiply(ratio).setScale(0, RoundingMode.HALF_UP);
            if (childPlanQuantity.compareTo(quantity) > 0) {
                childPlanQuantity = quantity;
            }
            BigDecimal childAssignQuantity = assignedQuantity.add(childPlanQuantity);
            if (childAssignQuantity.compareTo(planQuantity) > 0) {
                childPlanQuantity = childPlanQuantity.subtract(childAssignQuantity.subtract(planQuantity));
            }
            assignedQuantity = assignedQuantity.add(childPlanQuantity);

            // 创建派工计划
            WorkAssignmentVO workAssignment = createWorkAssign(planStartTime, planEndTime, childPlanQuantity, task, resource, planTaskOrderMap);
            workAssignmentList.add(workAssignment);
        }

        return workAssignmentList;
    }

    /**
     * 创建派工计划
     *
     * @param planStartTime    派工计划开始时间
     * @param planEndTime      派工计划结束时间
     * @param planQuantity     派工计划数量
     * @param task             待派工任务
     * @param resource         待派工任务所属资源
     * @param planTaskOrderMap 所有订单 Map<订单id, 订单信息>
     * @return 派工计划
     */
    private static WorkAssignmentVO createWorkAssign(LocalDateTime planStartTime,
                                                     LocalDateTime planEndTime,
                                                     BigDecimal planQuantity,
                                                     PlanTaskPO task,
                                                     ProductionResourcePO resource,
                                                     Map<String, PlanTaskOrderPO> planTaskOrderMap) {
        PlanTaskOrderPO taskOrder = planTaskOrderMap.get(task.getProductionOrderId());
        Duration planWorkHours = Optional.ofNullable(TaskDurationUtils.calculateTaskDuration(resource.getId(), task.getProcessInfo(), planQuantity))
                .orElseGet(() -> Duration.ZERO);
        return new WorkAssignmentVO()
                .setOrderCode(taskOrder.getOrderInfo().getCode())
                .setMaterialCode(taskOrder.getOrderMaterialInfo().getCode())
                .setScheduledQuantity(BigDecimalUtils.stripTrailingZeros(task.getScheduledQuantity()))
                .setProcessCode(task.getProcessInfo().getCode())
                .setPlanResourceCode(resource.getCode())
                .setPlanStartTime(planStartTime)
                .setPlanEndTime(planEndTime)
                .setPlanQuantity(BigDecimalUtils.stripTrailingZeros(planQuantity))
                .setPlanWorkHours(DurationUtils.formatDuration(DurationFormatTypeEnum.DAYS_HOURS_MINUTES_SECONDS, planWorkHours));
    }
}
