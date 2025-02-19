package cn.bctools.aps.service.facade.impl;

import cn.bctools.aps.dto.schedule.TaskDTO;
import cn.bctools.aps.entity.*;
import cn.bctools.aps.entity.dto.plan.PlanMaterialInfoDTO;
import cn.bctools.aps.entity.dto.plan.PlanOrderInfoDTO;
import cn.bctools.aps.entity.dto.plan.PlanTaskInputMaterialDTO;
import cn.bctools.aps.entity.enums.OrderSchedulingStatusEnum;
import cn.bctools.aps.entity.enums.OrderStatusEnum;
import cn.bctools.aps.entity.enums.OrderTypeEnum;
import cn.bctools.aps.entity.enums.PlanTaskStatusEnum;
import cn.bctools.aps.enums.DurationFormatTypeEnum;
import cn.bctools.aps.service.*;
import cn.bctools.aps.service.facade.PlanSolutionFacadeService;
import cn.bctools.aps.service.facade.TaskAdjustFacadeService;
import cn.bctools.aps.solve.model.*;
import cn.bctools.aps.solve.util.DurationUtils;
import cn.bctools.aps.util.BigDecimalUtils;
import cn.bctools.aps.util.PlanUtils;
import cn.bctools.aps.vo.schedule.report.PlanTaskGanttDetailVO;
import cn.bctools.aps.vo.schedule.report.PlanTaskInputMaterialVO;
import cn.bctools.common.utils.BeanCopyUtil;
import cn.bctools.common.utils.ObjectNull;
import cn.bctools.common.utils.TenantContextHolder;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * @author jvs
 * 排产方案聚合服务（处理已生成的排产结果）
 */
@Service
@AllArgsConstructor
public class PlanSolutionFacadeServiceImpl implements PlanSolutionFacadeService {

    private final ProductionOrderService productionOrderService;
    private final PlanTaskService planTaskService;
    private final PlanTaskPendingService planTaskPendingService;
    private final PlanTaskOrderService planTaskOrderService;
    private final PlanTaskOrderPendingService planTaskOrderPendingService;
    private final ProductionResourceService productionResourceService;
    private final MaterialService materialService;
    private final PlanTaskAdjustService planTaskAdjustService;
    private final TaskAdjustFacadeService taskAdjustFacadeService;
    private final PlanInfoService planInfoService;

    @Transactional(rollbackFor = Exception.class)
    @Override
    public void saveSolutionPending(BasicData basicData, SchedulingSolution schedulingSolution) {
        // 处理补充订单：若补充订单的订单号已存在，则使用已存在的订单id，替换自动生成的补充订单id
        List<ProductionOrder> taskOrderPendingList = basicData.getProductionOrders();
        // 查询已存在的补充订单
        Set<String> supplementOrderCodes = taskOrderPendingList.stream()
                .filter(ProductionOrder::getSupplement)
                .map(ProductionOrder::getCode)
                .collect(Collectors.toSet());
        // Map<订单号, 订单id>
        Map<String, String> existsSupplementOrderMap = productionOrderService.listByCodes(supplementOrderCodes)
                .stream()
                .collect(Collectors.toMap(ProductionOrderPO::getCode, ProductionOrderPO::getId));
        if (ObjectNull.isNotNull(existsSupplementOrderMap)) {
            taskOrderPendingList.forEach(order -> {
                if (order.getSupplement() && existsSupplementOrderMap.containsKey(order.getCode())) {
                    order.setId(existsSupplementOrderMap.get(order.getCode()));
                }
            });
        }

        // 保存待确认排产计划任务
        List<ProductionTask> tasks = schedulingSolution.getTasks();
        if (ObjectNull.isNotNull(tasks)) {
            List<PlanTaskPendingPO> planTaskPendingList = tasks.stream()
                    .map(task ->
                            BeanCopyUtil.copy(task, PlanTaskPendingPO.class)
                                    .setProductionOrderId(Optional.ofNullable(task.getOrder()).map(ProductionOrder::getId).orElseGet(() -> null))
                                    .setColor(Optional.ofNullable(task.getOrder()).map(ProductionOrder::getColor).orElseGet(() -> null))
                                    .setScheduledQuantity(task.getQuantity())
                                    .setMainResourceId(task.getResource().getId())
                                    .setProcessInfo(task.getProcess())
                                    .setPinned(false)
                    )
                    .toList();
            planTaskPendingService.saveBatch(planTaskPendingList);
        }

        // 保存待确认排产计划订单
        // 获取订单最后一个任务最晚计划完成时间
        Function<String, LocalDateTime> getOrderTaskEndTimeFunction = orderId -> {
            List<ProductionTask> taskList = tasks.stream()
                    .filter(task -> ObjectNull.isNotNull(task.getOrder()))
                    .filter(task -> task.getOrder().getId().equals(orderId) && task.getEndTask())
                    .collect(Collectors.toList());
            if (ObjectNull.isNull(taskList)) {
                return null;
            }
            // 若主订单id与订单id不同相同，则找主订单
            String mainOrderId = taskList.get(0).getMainOrderId();
            if (!mainOrderId.equals(orderId)) {
                taskList = tasks.stream()
                        .filter(task -> ObjectNull.isNotNull(task.getOrder()))
                        .filter(task -> task.getMainOrderId().equals(mainOrderId) && task.getEndTask())
                        .collect(Collectors.toList());
            }
            // 取最晚计划结束时间
            return taskList.stream().map(ProductionTask::getEndTime).max(Comparator.naturalOrder()).get();

        };
        // 转换为持久化数据结构
        List<PlanTaskOrderPendingPO> planTaskOrderPendingList = taskOrderPendingList.stream()
                .map(order -> {
                    PlanOrderInfoDTO planOrderInfo = BeanCopyUtil.copy(order, PlanOrderInfoDTO.class);
                    Material material = basicData.getMaterialMap().get(planOrderInfo.getMaterialId());
                    // 获取订单最后一个任务的计划完成时间
                    LocalDateTime taskEndTime = null;
                    if (ObjectNull.isNotNull(tasks)) {
                        taskEndTime = getOrderTaskEndTimeFunction.apply(order.getId());
                    }
                    String delyTimeString = PlanUtils.formatDelayTimeString(taskEndTime, order.getDeliveryTime());
                    // 转换订单信息
                    PlanMaterialInfoDTO planMaterialInfo = BeanCopyUtil.copy(material, PlanMaterialInfoDTO.class);
                    return new PlanTaskOrderPendingPO()
                            .setOrderId(order.getId())
                            .setOrderCode(order.getCode())
                            .setDelayTimeString(delyTimeString)
                            .setOrderInfo(planOrderInfo)
                            .setOrderMaterialInfo(planMaterialInfo);
                })
                .toList();
        planTaskOrderPendingService.saveBatch(planTaskOrderPendingList);

        // 创建待确认排产计划基本信息
        if (ObjectNull.isNotNull(tasks)) {
            planInfoService.createUnconfirmedPlan(schedulingSolution.getStrategy());
        }
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public void removeSolutionPending() {
        String tenantId = TenantContextHolder.getTenantId();
        // 删除待确认排产计划任务
        planTaskPendingService.remove(Wrappers.<PlanTaskPendingPO>lambdaQuery()
                .eq(PlanTaskPendingPO::getTenantId, tenantId));
        // 删除待确认排产计划订单
        planTaskOrderPendingService.remove(Wrappers.<PlanTaskOrderPendingPO>lambdaQuery()
                .eq(PlanTaskOrderPendingPO::getTenantId, tenantId));
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public void saveSolution() {
        // 转储待确认的排产任务保存到排产计划
        List<PlanTaskPendingPO> planTaskPendingList = planTaskPendingService.list();
        if (ObjectNull.isNotNull(planTaskPendingList)) {
            // 查询已存在的任务 Map<任务编码, 任务>
            Set<String> taskCodes = planTaskPendingList.stream().map(PlanTaskPendingPO::getCode).collect(Collectors.toSet());
            Map<String, TaskDTO> existsTaskMap = taskAdjustFacadeService.listTaskByCodes(taskCodes).stream()
                    .collect(Collectors.toMap(TaskDTO::getCode, Function.identity()));

            // 更新已有任务部分数据
            List<PlanTaskPO> tasks = planTaskPendingList.stream()
                    .map(pendingTask -> {
                        PlanTaskPO task = BeanCopyUtil.copy(pendingTask, PlanTaskPO.class)
                                .setTaskStatus(PlanTaskStatusEnum.PENDING)
                                .setProcessCode(pendingTask.getProcessInfo().getCode())
                                .setQuantityCompleted(BigDecimal.ZERO);
                        // 若任务编码已存在，则更新
                        TaskDTO existsTask = existsTaskMap.get(pendingTask.getCode());
                        if (ObjectNull.isNotNull(existsTask)) {
                            task.setTaskStatus(existsTask.getTaskStatus())
                                    .setLastCompletionTime(existsTask.getLastCompletionTime())
                                    .setQuantityCompleted(existsTask.getQuantityCompleted());
                        }
                        return task;
                    })
                    .toList();

            // 删除待确认任务已存在的排产任务，或未锁定且未完成的任务
            List<String> taskIds = planTaskPendingList.stream().map(PlanTaskPendingPO::getId).toList();
            LambdaQueryWrapper<PlanTaskPO> removeWrapper = Wrappers.<PlanTaskPO>lambdaQuery()
                    .in(PlanTaskPO::getId, taskIds)
                    .or(o ->
                            o.eq(PlanTaskPO::getPinned, Boolean.FALSE)
                            .ne(PlanTaskPO::getTaskStatus, PlanTaskStatusEnum.COMPLETED)
                    );
            planTaskService.remove(removeWrapper);

            // 保存新的任务计划
            planTaskService.saveBatch(tasks);
        }

        // 转储待确认排产计划订单 Map<订单id， 任务订单信息>
        Map<String, PlanTaskOrderPendingPO> planTaskOrderPendingMap = planTaskOrderPendingService.list()
                .stream()
                .collect(Collectors.toMap(PlanTaskOrderPendingPO::getOrderId, Function.identity()));
        if (ObjectNull.isNotNull(planTaskOrderPendingMap)) {
            // 保存排产任务订单信息
            List<PlanTaskOrderPO> schedulingOrderList = planTaskOrderPendingMap.values().stream()
                    .map(order -> BeanCopyUtil.copy(order, PlanTaskOrderPO.class))
                    .toList();
            if (ObjectNull.isNotNull(schedulingOrderList)) {
                List<String> schedulingOrderIds = schedulingOrderList.stream().map(PlanTaskOrderPO::getOrderId).toList();
                planTaskOrderService.removeByOrderIds(schedulingOrderIds);
                planTaskOrderService.saveBatch(schedulingOrderList);
            }

            // 修改订单排程状态
            // 查询未结束的订单 Map<订单id, 订单信息>
            Map<String, ProductionOrderPO> existsOroductionOrderMap = productionOrderService.list(Wrappers.<ProductionOrderPO>lambdaQuery()
                            .select(ProductionOrderPO::getId, ProductionOrderPO::getSchedulingStatus)
                            .eq(ProductionOrderPO::getOrderStatus, OrderStatusEnum.PENDING))
                    .stream()
                    .map(order -> {
                        PlanTaskOrderPendingPO taskOrderPending = planTaskOrderPendingMap.get(order.getId());
                        if (ObjectNull.isNotNull(taskOrderPending)) {
                            order.setSchedulingStatus(taskOrderPending.getOrderInfo().getSchedulingStatus());
                        } else {
                            // 修改订单排产状态
                            // 不修改排产状态为已完成的订单信息
                            if (!OrderSchedulingStatusEnum.COMPLETED.equals(order.getSchedulingStatus())) {
                                order.setSchedulingStatus(OrderSchedulingStatusEnum.UNSCHEDULED);
                            }
                        }
                        return order;
                    })
                    .collect(Collectors.toMap(ProductionOrderPO::getId, Function.identity()));
            // Map<true-修改false新增，订单集合>
            List<ProductionOrderPO> newProductionOrderList = planTaskOrderPendingMap.entrySet().stream()
                    .map(orderPending -> {
                        PlanTaskOrderPendingPO taskOrderPending = orderPending.getValue();
                        ProductionOrderPO order = existsOroductionOrderMap.get(taskOrderPending.getOrderId());
                        if (ObjectNull.isNull(order)) {
                            PlanOrderInfoDTO orderInfo = taskOrderPending.getOrderInfo();
                            return new ProductionOrderPO()
                                    .setId(taskOrderPending.getOrderId())
                                    .setCode(orderInfo.getCode())
                                    .setMaterialCode(orderInfo.getMaterialCode())
                                    .setQuantity(orderInfo.getQuantity())
                                    .setDeliveryTime(orderInfo.getDeliveryTime())
                                    .setPriority(0)
                                    .setSequence(new BigDecimal("0"))
                                    .setType(OrderTypeEnum.MANUFACTURE)
                                    .setOrderStatus(OrderStatusEnum.PENDING)
                                    .setSchedulingStatus(OrderSchedulingStatusEnum.SCHEDULED)
                                    .setColor(orderInfo.getColor())
                                    .setCanSchedule(true)
                                    .setSupplement(orderInfo.getSupplement())
                                    .setParentOrderCode(orderInfo.getParentOrderCode());
                        }
                        return null;
                    })
                    .filter(ObjectNull::isNotNull)
                    .toList();

            if (ObjectNull.isNotNull(existsOroductionOrderMap)) {
                productionOrderService.updateBatchById(existsOroductionOrderMap.values());
            }
            if (ObjectNull.isNotNull(newProductionOrderList)) {
                productionOrderService.saveBatch(newProductionOrderList);
            }
        }

        // 清除待确认任务
        removeSolutionPending();

        // 清除已调整任务
        planTaskAdjustService.remove(Wrappers.<PlanTaskAdjustPO>lambdaQuery()
                .eq(PlanTaskAdjustPO::getTenantId, TenantContextHolder.getTenantId()));

        // 保存排程计划基本信息
        LocalDateTime earliestTaskStartTime = planTaskService.getEarliestTaskStartTime();
        planInfoService.confirmedPlan(earliestTaskStartTime);
    }

    @Override
    public boolean existsUnconfirmedTask() {
        long taskPendingCount = planTaskPendingService.count();
        long taskOrderPendingCount = planTaskOrderPendingService.count();
        return taskPendingCount > 0 || taskOrderPendingCount >0;
    }

    @Override
    public PlanTaskGanttDetailVO getPlanTaskGanttDetail(String taskCode) {
        TaskDTO planTask = taskAdjustFacadeService.getTaskByCode(taskCode);
        // 合并任务，查询其子任务
        List<PlanTaskGanttDetailVO> childTaskList = null;
        if (planTask.getMergeTask()) {
            List<TaskDTO> childTasks = taskAdjustFacadeService.listTaskByMergeTaskCode(planTask.getCode())
                    .stream()
                    .filter(task -> !task.getDiscard())
                    .toList();
            List<String> childOrderIds = childTasks.stream().map(TaskDTO::getProductionOrderId).toList();
            // 子任务订单信息
            Map<String, PlanTaskOrderPO> planTaskOrderMap = planTaskOrderService.listByOrderIds(childOrderIds).stream()
                    .collect(Collectors.toMap(PlanTaskOrderPO::getOrderId, Function.identity()));
            childTaskList = childTasks.stream()
                    .map(childTask -> {
                        PlanTaskGanttDetailVO vo = BeanCopyUtil.copy(childTask, PlanTaskGanttDetailVO.class)
                                .setScheduledQuantity(BigDecimalUtils.stripTrailingZeros(childTask.getScheduledQuantity()))
                                .setProcessCode(childTask.getProcessInfo().getCode())
                                .setProcessName(childTask.getProcessInfo().getName())
                                .setQuantityCompleted(childTask.getQuantityCompleted());
                        PlanTaskOrderPO order = planTaskOrderMap.get(childTask.getProductionOrderId());
                        if (ObjectNull.isNotNull(order)) {
                            vo.setOrderCode(order.getOrderCode())
                                    .setMaterialCode(order.getOrderMaterialInfo().getCode())
                                    .setMaterialName(order.getOrderMaterialInfo().getName())
                                    .setOrderMaterialQuantity(BigDecimalUtils.stripTrailingZeros(order.getOrderInfo().getQuantity()));
                        }
                        return vo;
                    })
                    .toList();

        }
        // 获取订单信息
        PlanTaskOrderPO planTaskOrder = planTaskOrderService.getByOrderId(planTask.getProductionOrderId());
        // 获取主资源信息
        ProductionResourcePO productionResource = Optional.ofNullable(productionResourceService.getById(planTask.getMainResourceId()))
                .orElseGet(ProductionResourcePO::new);
        // 填充任务详情
        PlanTaskGanttDetailVO detail = BeanCopyUtil.copy(planTask, PlanTaskGanttDetailVO.class)
                .setScheduledQuantity(BigDecimalUtils.stripTrailingZeros(planTask.getScheduledQuantity()))
                .setProcessCode(planTask.getProcessInfo().getCode())
                .setProcessName(planTask.getProcessInfo().getName())
                .setMainResourceCode(productionResource.getCode())
                .setMainResourceName(productionResource.getName())
                .setTotalTimeString(DurationUtils.formatDuration(DurationFormatTypeEnum.DAYS_HOURS_MINUTES_SECONDS, Duration.between(planTask.getStartTime(), planTask.getEndTime())))
                .setQuantityCompleted(planTask.getQuantityCompleted())
                .setMergeTask(planTask.getMergeTask())
                .setChildTasks(childTaskList)
                .setAdjusted(planTask.getAdjusted());

        if (ObjectNull.isNotNull(planTaskOrder)) {
            detail.setOrderCode(planTaskOrder.getOrderCode())
                    .setMaterialCode(planTaskOrder.getOrderMaterialInfo().getCode())
                    .setMaterialName(planTaskOrder.getOrderMaterialInfo().getName())
                    .setOrderMaterialQuantity(BigDecimalUtils.stripTrailingZeros(planTaskOrder.getOrderInfo().getQuantity()));
        }

        detail.setCompletionTime(planTask.getLastCompletionTime());

        // 输入物料
        if (ObjectNull.isNotNull(planTask.getInputMaterials())) {
            Set<String> materialIds = planTask.getInputMaterials().stream()
                    .map(PlanTaskInputMaterialDTO::getId)
                    .collect(Collectors.toSet());
            Map<String, MaterialPO> materialMap = materialService.listByIds(materialIds).stream()
                    .collect(Collectors.toMap(MaterialPO::getId, Function.identity()));
            List<PlanTaskInputMaterialVO> inputMaterials = planTask.getInputMaterials().stream()
                    .map(material -> {
                        MaterialPO materialPo = materialMap.get(material.getId());
                        return new PlanTaskInputMaterialVO()
                                .setCode(materialPo.getCode())
                                .setName(materialPo.getName())
                                .setQuantity(BigDecimalUtils.stripTrailingZeros(material.getQuantity()));
                    })
                    .toList();
            detail.setInputMaterials(inputMaterials);
        }

        return detail;
    }
}
