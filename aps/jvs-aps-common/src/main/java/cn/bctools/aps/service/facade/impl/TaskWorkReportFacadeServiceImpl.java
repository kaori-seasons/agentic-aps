package cn.bctools.aps.service.facade.impl;

import cn.bctools.aps.dto.schedule.UpdateWorkReportProgress;
import cn.bctools.aps.dto.schedule.WorkReportDTO;
import cn.bctools.aps.entity.*;
import cn.bctools.aps.entity.enums.PlanTaskStatusEnum;
import cn.bctools.aps.entity.enums.OrderSchedulingStatusEnum;
import cn.bctools.aps.service.*;
import cn.bctools.aps.service.facade.TaskWorkReportFacadeService;
import cn.bctools.aps.util.PlanUtils;
import cn.bctools.common.exception.BusinessException;
import cn.bctools.common.utils.BeanCopyUtil;
import cn.bctools.common.utils.ObjectNull;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.experimental.Accessors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * @author jvs
 * 任务报工聚合服务
 */
@Service
@AllArgsConstructor
public class TaskWorkReportFacadeServiceImpl implements TaskWorkReportFacadeService {

    private final WorkReportService workReportService;
    private final PlanTaskService planTaskService;
    private final PlanTaskOrderService planTaskOrderService;
    private final ProductionResourceService productionResourceService;
    private final ProductionOrderService productionOrderService;

    @Transactional(rollbackFor = Exception.class)
    @Override
    public void importBatchWorkReportRefreshTaskProgress(List<WorkReportDTO> workReportList) {
        // 过滤没有报工时间，没有报工数量的记录
        List<WorkReportDTO> workReports = workReportList.stream()
                .filter(report ->
                        ObjectNull.isNotNull(report.getReportTime())
                        && ObjectNull.isNotNull(report.getQuantityCompleted())
                )
                .collect(Collectors.toList());
        if (ObjectNull.isNull(workReports)) {
            return;
        }
        // 校验
        Set<String> reportSet = new HashSet<>();
        workReports.forEach(report -> {
            if (ObjectNull.isNull(report.getOrderCode())) {
                throw new BusinessException("订单不能为空");
            }
            if (ObjectNull.isNull(report.getProcessCode())) {
                throw new BusinessException("工序不能为空");
            }
            if (ObjectNull.isNull(report.getPlanResourceCode())) {
                throw new BusinessException("计划主资源不能为空");
            }
            if (ObjectNull.isNull(report.getPlanStartTime())) {
                throw new BusinessException("计划开始时间不能为空");
            }
            if (report.getQuantityCompleted().compareTo(BigDecimal.ZERO) < 0) {
                throw new BusinessException("实际完成数量不能为负数");
            }
            String str = report.getOrderCode() +
                    report.getProcessCode() +
                    report.getPlanResourceCode() +
                    report.getPlanStartTime() +
                    report.getReportTime();
            if (reportSet.contains(str)) {
                throw new BusinessException("不能存在重复报工数据");
            }
            reportSet.add(str);
        });

        // 保存报工记录，得到本次报工相关的所有报工信息
        List<WorkReportUpdateRecord> updateRecordList = saveWorkReport(workReports);

        // 修改任务进度
        List<PlanTaskPO> changeProgressTaskList = updateTaskProgress(updateRecordList);

        // 修改订单排程状态
        updateOrderSchedulingStatus(changeProgressTaskList);
    }

    @Override
    public void updateTaskProgress(UpdateWorkReportProgress updateReport) {
        PlanTaskPO planTask = planTaskService.getByCode(updateReport.getTaskCode());
        if (ObjectNull.isNull(planTask)) {
            return;
        }
        if (planTask.getMergeTask()) {
            throw new BusinessException("不能修改合并任务报工");
        }
        planTask.setQuantityCompleted(updateReport.getQuantityCompleted())
                .setLastCompletionTime(updateReport.getEndTime())
                .setTaskStatus(PlanUtils.calculateTaskStatus(planTask.getScheduledQuantity(), planTask.getQuantityCompleted()));

        List<PlanTaskPO> changeProgressTaskList = new ArrayList<>();
        changeProgressTaskList.add(planTask);

        // 若修改的是合并任务的子任务，则需要重新计算合并任务的报工
        if (ObjectNull.isNotNull(planTask.getMergeTaskCode())) {
            PlanTaskPO mergeTask = planTaskService.getByCode(planTask.getMergeTaskCode());
            List<PlanTaskPO> mergeChildTasks = planTaskService.listTaskByMergeTaskCode(planTask.getMergeTaskCode());
            BigDecimal totalQuantityCompleted = mergeChildTasks.stream()
                    .filter(childTask -> !childTask.getCode().equals(planTask.getCode()))
                    .map(PlanTaskPO::getQuantityCompleted)
                    .reduce(BigDecimal.ZERO, BigDecimal::add)
                    .add(planTask.getQuantityCompleted());
            LocalDateTime lastCompletionTime = mergeChildTasks.stream()
                    .filter(childTask -> !childTask.getCode().equals(planTask.getCode()))
                    .map(PlanTaskPO::getLastCompletionTime)
                    .filter(ObjectNull::isNotNull)
                    .max(Comparator.naturalOrder())
                    .orElseGet(() -> null);
            if (ObjectNull.isNull(lastCompletionTime) || planTask.getLastCompletionTime().isAfter(lastCompletionTime)) {
                lastCompletionTime = planTask.getLastCompletionTime();
            }
            mergeTask.setTaskStatus(PlanUtils.calculateTaskStatus(mergeTask.getScheduledQuantity(), totalQuantityCompleted))
                    .setQuantityCompleted(totalQuantityCompleted)
                    .setLastCompletionTime(lastCompletionTime);
            changeProgressTaskList.add(mergeTask);
        }

        // 修改任务进度
        planTaskService.updateBatchById(changeProgressTaskList);

        // 修改订单排程状态
        updateOrderSchedulingStatus(changeProgressTaskList);
    }

    /**
     * 保存报工记录
     *
     * @param workReports 本次报工记录
     * @return 可更新任务进度的报工记录
     */
    private List<WorkReportUpdateRecord> saveWorkReport(List<WorkReportDTO> workReports) {
        // 查询资源
        Set<String> planResourceCodes = workReports.stream()
                .flatMap(workReport -> Stream.of(workReport.getPlanResourceCode(), workReport.getResourceCode()))
                .collect(Collectors.toSet());
        Map<String, String> resourceMap = productionResourceService.listByCodes(planResourceCodes).stream()
                .collect(Collectors.toMap(ProductionResourcePO::getCode, ProductionResourcePO::getId));

        // 查询订单
        Set<String> orderCodes = workReports.stream()
                .map(WorkReportDTO::getOrderCode)
                .filter(ObjectNull::isNotNull)
                .collect(Collectors.toSet());
        Map<String, String> orderMap = planTaskOrderService.list(Wrappers.<PlanTaskOrderPO>lambdaQuery().in(PlanTaskOrderPO::getOrderCode, orderCodes)).stream()
                .collect(Collectors.toMap(PlanTaskOrderPO::getOrderCode, PlanTaskOrderPO::getOrderId));

        // 筛选已存在的报工记录
        List<WorkReportPO> existsWorkReports = workReportService.list(Wrappers.<WorkReportPO>lambdaQuery().in(WorkReportPO::getOrderCode, orderCodes))
                .stream()
                .filter(existsReport ->
                        workReports.stream()
                                .anyMatch(report ->
                                        existsReport.getOrderCode().equals(report.getOrderCode())
                                                && existsReport.getProcessCode().equals(report.getProcessCode())
                                                && existsReport.getPlanResourceCode().equals(report.getPlanResourceCode()))
                ).toList();

        // 可更新任务进度的报工记录
        List<WorkReportUpdateRecord> workReportUpdateRecordList = new ArrayList<>();

        // 得到待保存的报工记录 key:true-新增，false-修改
        Map<Boolean, List<WorkReportPO>> changeReportMap = workReports.stream()
                .map(report -> {
                    Optional<WorkReportPO> optionalExistsWorkReport = existsWorkReports.stream()
                            .filter(existsReport ->
                                    existsReport.getOrderCode().equals(report.getOrderCode())
                                            && existsReport.getProcessCode().equals(report.getProcessCode())
                                            && existsReport.getPlanResourceCode().equals(report.getPlanResourceCode())
                                            && existsReport.getPlanStartTime().isEqual(report.getPlanStartTime())
                                            && existsReport.getReportTime().isEqual(report.getReportTime()))
                            .findFirst();
                    // 有已存在的报工记录
                    if (optionalExistsWorkReport.isPresent()) {
                        WorkReportPO existsWorkReport = optionalExistsWorkReport.get();
                        BigDecimal reportQuantityDelta = report.getQuantityCompleted().subtract(existsWorkReport.getQuantityCompleted());
                        if (reportQuantityDelta.compareTo(BigDecimal.ZERO) != 0) {
                            WorkReportUpdateRecord updateRecord = BeanCopyUtil.copy(existsWorkReport, WorkReportUpdateRecord.class)
                                    .setReportQuantityDelta(reportQuantityDelta);
                            workReportUpdateRecordList.add(updateRecord);
                        }

                        // 更新记录为新的报工数据
                        return existsWorkReport
                                .setQuantityCompleted(report.getQuantityCompleted())
                                .setStartTime(report.getStartTime())
                                .setEndTime(report.getEndTime())
                                .setResourceCode(report.getResourceCode())
                                .setResourceId(resourceMap.get(report.getResourceCode()));
                    }
                    // 未找到已存在的报工记录，转为待新增报工记录
                    return BeanCopyUtil.copy(report, WorkReportPO.class)
                            .setOrderId(orderMap.get(report.getOrderCode()))
                            .setPlanResourceId(resourceMap.get(report.getPlanResourceCode()))
                            .setResourceId(resourceMap.get(report.getResourceCode()));
                })
                .filter(ObjectNull::isNotNull)
                .collect(Collectors.groupingBy(r -> ObjectNull.isNull(r.getId())));
        // 更新已存在的报工
        List<WorkReportPO> updateReportList = changeReportMap.get(Boolean.FALSE);
        if (ObjectNull.isNotNull(updateReportList)) {
            workReportService.updateBatchById(updateReportList);
        }
        // 新增报工
        List<WorkReportPO> newReportList = changeReportMap.get(Boolean.TRUE);
        if (ObjectNull.isNotNull(newReportList)) {
            workReportService.saveBatch(newReportList);
            // 将所有新增的报工记录，加入可更新任务进度的报工记录
            workReportUpdateRecordList.addAll(newReportList.stream()
                    .map(report -> BeanCopyUtil.copy(report, WorkReportUpdateRecord.class)
                            .setReportQuantityDelta(report.getQuantityCompleted()))
                    .toList());
        }

        return workReportUpdateRecordList;
    }


    /**
     * 修改任务进度
     *
     * @param updateRecordList 可更新任务进度的报工记录
     */
    private List<PlanTaskPO> updateTaskProgress(List<WorkReportUpdateRecord> updateRecordList) {
        if (ObjectNull.isNull(updateRecordList)) {
            return Collections.emptyList();
        }
        // 将本次报工相关的任务
        Set<String> orderIds = updateRecordList.stream().map(WorkReportUpdateRecord::getOrderId).collect(Collectors.toSet());
        Map<String, List<PlanTaskPO>> groupTaskMap = planTaskService.list(Wrappers.<PlanTaskPO>lambdaQuery()
                        .select(PlanTaskPO::getId, PlanTaskPO::getProductionOrderId, PlanTaskPO::getProcessCode,
                                PlanTaskPO::getMainResourceId, PlanTaskPO::getStartTime, PlanTaskPO::getEndTime,
                                PlanTaskPO::getScheduledQuantity, PlanTaskPO::getQuantityCompleted, PlanTaskPO::getEndTask)
                        .in(PlanTaskPO::getProductionOrderId, orderIds))
                .stream()
                .filter(task ->
                        updateRecordList.stream().anyMatch(report ->
                                task.getProductionOrderId().equals(report.getOrderId())
                                        && task.getProcessCode().equals(report.getProcessCode())
                                        && task.getMainResourceId().equals(report.getPlanResourceId())))
                .collect(Collectors.groupingBy(task -> task.getProductionOrderId() + task.getProcessCode() + task.getMainResourceId()));

        Map<String, List<WorkReportUpdateRecord>> groupReportMap = updateRecordList.stream()
                .collect(Collectors.groupingBy(report -> report.getOrderId() + report.getProcessCode() + report.getPlanResourceId()));

        // 修改任务已完成数量
        List<PlanTaskPO> changeProgressTaskList = groupReportMap.entrySet().stream()
                .map(e -> {
                    List<WorkReportUpdateRecord> reports = e.getValue();
                    List<PlanTaskPO> tasks = groupTaskMap.get(e.getKey()).stream()
                            .sorted(Comparator.comparing(PlanTaskPO::getStartTime))
                            .toList();
                    tasks.forEach(task -> {
                        List<WorkReportUpdateRecord> taskReportList = reports.stream()
                                .filter(report ->
                                        !task.getStartTime().isAfter(report.getPlanStartTime())
                                                && report.getPlanStartTime().isBefore(task.getEndTime()))
                                .toList();

                        // 最晚报工完成时间
                        LocalDateTime lastCompletionTime = taskReportList.stream()
                                .map(WorkReportUpdateRecord::getEndTime)
                                .filter(ObjectNull::isNotNull)
                                .max(Comparator.naturalOrder())
                                .orElseGet(() -> null);
                        if (ObjectNull.isNull(lastCompletionTime)) {
                            lastCompletionTime = taskReportList.stream()
                                    .map(WorkReportUpdateRecord::getReportTime)
                                    .filter(ObjectNull::isNotNull)
                                    .max(Comparator.naturalOrder())
                                    .orElseGet(() -> null);
                        }

                        // 本次报工变化量总数
                        BigDecimal totalQuantityCompletedDelta = taskReportList.stream()
                                .map(WorkReportUpdateRecord::getReportQuantityDelta)
                                .reduce(BigDecimal.ZERO, BigDecimal::add);

                        task.setQuantityCompleted(task.getQuantityCompleted().add(totalQuantityCompletedDelta))
                                .setLastCompletionTime(lastCompletionTime);

                        // 修改任务进度
                        task.setTaskStatus(PlanUtils.calculateTaskStatus(task.getScheduledQuantity(), task.getQuantityCompleted()));
                    });
                    return tasks;
                })
                .flatMap(Collection::stream)
                .collect(Collectors.toList());

        // 若是合并任务的子任务变更了进度，则需要更新合并任务的进度
        List<PlanTaskPO> changeMergeTaskList = updateMergeTaskProgress(changeProgressTaskList);
        if (ObjectNull.isNotNull(changeMergeTaskList)) {
            changeProgressTaskList.addAll(changeMergeTaskList);
        }

        // 修改任务进度
        planTaskService.updateBatchById(changeProgressTaskList);

        return changeProgressTaskList;
    }

    /**
     * 更新合并任务的进度
     * <p>
     * 若是合并任务的子任务变更了进度，则需要更新合并任务的进度
     *
     * @param changeProgressTaskList 变更进度的任务集合
     * @return 变更进度后的合并任务集合
     */
    private List<PlanTaskPO> updateMergeTaskProgress(List<PlanTaskPO> changeProgressTaskList) {
        // 合并任务的编码
        Set<String> mergeTaskCodes = new HashSet<>();
        // 合并任务的子任务
        Map<String, PlanTaskPO> changeMergeChildProgressTaskMap = new HashMap<>();
        changeProgressTaskList.forEach(changeProgressTask -> {
            String mergeTaskCode = changeProgressTask.getMergeTaskCode();
            if (ObjectNull.isNotNull(mergeTaskCode)) {
                mergeTaskCodes.add(mergeTaskCode);
                changeMergeChildProgressTaskMap.put(changeProgressTask.getCode(), changeProgressTask);
            }
        });
        if (ObjectNull.isNull(mergeTaskCodes)) {
            return Collections.emptyList();
        }

        // 查询合并任务
        List<PlanTaskPO> mergeTaskList = planTaskService.listByCodes(mergeTaskCodes);

        // 查询合并任务的所有子任务
        Map<String, List<PlanTaskPO>> mergeTaskChildMap = planTaskService.listTaskByMergeTaskCodes(mergeTaskCodes).stream()
                .collect(Collectors.groupingBy(PlanTaskPO::getMergeTaskCode));

        // 更新合并任务进度
        mergeTaskList.forEach(mergeTask -> {
            // 合并任务的子任务
            List<PlanTaskPO> childTaskList = mergeTaskChildMap.get(mergeTask.getCode());
            BigDecimal totalQuantityCompleted = BigDecimal.ZERO;
            LocalDateTime lastCompletionTime = null;
            for (PlanTaskPO childTask : childTaskList) {
                BigDecimal childQuantityCompleted = Optional.ofNullable(childTask.getQuantityCompleted()).orElseGet(() -> BigDecimal.ZERO);
                LocalDateTime childLastCompletionTime = childTask.getLastCompletionTime();
                if (changeMergeChildProgressTaskMap.containsKey(childTask.getCode())) {
                    PlanTaskPO changeChildTask = changeMergeChildProgressTaskMap.get(childTask.getCode());
                    childQuantityCompleted = changeChildTask.getQuantityCompleted();
                    childLastCompletionTime = changeChildTask.getLastCompletionTime();
                }
                totalQuantityCompleted = totalQuantityCompleted.add(childQuantityCompleted);
                if (ObjectNull.isNotNull(childLastCompletionTime) && (ObjectNull.isNull(lastCompletionTime) || childLastCompletionTime.isAfter(lastCompletionTime))) {
                    lastCompletionTime = childLastCompletionTime;
                }
            }
            // 修改任务进度
            mergeTask.setTaskStatus(PlanUtils.calculateTaskStatus(mergeTask.getScheduledQuantity(), totalQuantityCompleted))
                    .setQuantityCompleted(totalQuantityCompleted)
                    .setLastCompletionTime(lastCompletionTime);
        });

        return mergeTaskList;
    }

    /**
     * 修改订单排程进度
     *
     * @param changeMergeTaskList 变更进度的任务
     */
    private void updateOrderSchedulingStatus(List<PlanTaskPO> changeMergeTaskList) {
        // 筛选订单生产任务链中标记为最后一个任务的任务
        Set<String> orderIds = changeMergeTaskList.stream()
                .filter(task -> Boolean.TRUE.equals(task.getEndTask()))
                .map(PlanTaskPO::getProductionOrderId)
                .filter(ObjectNull::isNotNull)
                .collect(Collectors.toSet());
        if (ObjectNull.isNull(orderIds)) {
            return;
        }
        Map<String, List<PlanTaskPO>> groupTaskMap = planTaskService.list(Wrappers.<PlanTaskPO>lambdaQuery()
                        .select(PlanTaskPO::getProductionOrderId, PlanTaskPO::getProcessCode, PlanTaskPO::getTaskStatus)
                        .in(PlanTaskPO::getProductionOrderId, orderIds)
                        .eq(PlanTaskPO::getEndTask, true))
                .stream()
                .collect(Collectors.groupingBy(PlanTaskPO::getProductionOrderId));

        // 待修改订单排程状态
        List<ProductionOrderPO> orderList = groupTaskMap.entrySet().stream()
                .map(task -> {
                    boolean allCompleted = task.getValue().stream()
                            .allMatch(t -> PlanTaskStatusEnum.COMPLETED.equals(t.getTaskStatus()));
                    ProductionOrderPO productionOrder = new ProductionOrderPO()
                            .setId(task.getKey());
                    if (allCompleted) {
                        productionOrder.setSchedulingStatus(OrderSchedulingStatusEnum.COMPLETED);
                    } else {
                        productionOrder.setSchedulingStatus(OrderSchedulingStatusEnum.SCHEDULED);
                    }
                    return productionOrder;
                })
                .toList();
        productionOrderService.updateBatchById(orderList);
    }


    /**
     * 可更新任务进度的报工记录
     */
    @Data
    @Accessors(chain = true)
    private static class WorkReportUpdateRecord {
        /**
         * 订单id
         */
        private String orderId;

        /**
         * 工序编码
         */
        private String processCode;

        /**
         * 计划主资源id
         */
        private String planResourceId;

        /**
         * 计划开始时间
         */
        private LocalDateTime planStartTime;

        /**
         * 任务报工数量的变化量
         * <p>
         * 用于更新任务的已完成数量。
         * 公式：
         * 报工数量的变化量 = 新报工数量 - 旧报工数量；
         * 任务已完成数量 = 任务已完成数量 + 报工数量的变化量
         */
        private BigDecimal reportQuantityDelta;

        /**
         * 实际结束时间
         */
        private LocalDateTime endTime;

        /**
         * 报工时间
         */
        private LocalDateTime reportTime;
    }
}
