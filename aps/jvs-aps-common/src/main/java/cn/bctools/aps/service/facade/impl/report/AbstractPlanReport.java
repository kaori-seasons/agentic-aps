package cn.bctools.aps.service.facade.impl.report;

import cn.bctools.aps.dto.schedule.PlanScheduleReportDTO;
import cn.bctools.aps.dto.schedule.TaskDTO;
import cn.bctools.aps.entity.PlanTaskOrderPO;
import cn.bctools.aps.entity.PlanTaskOrderPendingPO;
import cn.bctools.aps.service.PlanTaskOrderPendingService;
import cn.bctools.aps.service.PlanTaskOrderService;
import cn.bctools.aps.service.PlanTaskPendingService;
import cn.bctools.aps.service.facade.TaskAdjustFacadeService;
import cn.bctools.common.exception.BusinessException;
import cn.bctools.common.utils.BeanCopyUtil;
import cn.bctools.common.utils.ObjectNull;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * @author jvs
 * 任务报告顶级抽象类
 */
public abstract class AbstractPlanReport {

    @Resource
    private TaskAdjustFacadeService taskAdjustFacadeService;
    @Resource
    private PlanTaskPendingService planTaskPendingService;
    @Resource
    private PlanTaskOrderService planTaskOrderService;
    @Resource
    private PlanTaskOrderPendingService planTaskOrderPendingService;

    /**
     * 查询预览排产计划
     *
     * @return 日期范围内的排产计划
     */
    protected List<TaskDTO> listPreviewReportTask() {
        return planTaskPendingService.list()
                .stream()
                .map(task -> BeanCopyUtil.copy(task, TaskDTO.class)
                        .setQuantityCompleted(BigDecimal.ZERO)
                        .setDiscard(false)
                        .setCompliant(true))
                .sorted(Comparator.comparing(TaskDTO::getStartTime))
                .toList();
    }


    /**
     * 获取报告日期范围
     *
     * @param dateRange 日期范围
     * @return 日期范围
     */
    private List<LocalDateTime> getReportDateRange(List<LocalDateTime> dateRange) {
        LocalDateTime beginDate = null;
        LocalDateTime endDate = null;
        if (ObjectNull.isNull(dateRange)) {
            beginDate = Optional.ofNullable(taskAdjustFacadeService.getEarliestTaskStartTime())
                    .orElseGet(LocalDateTime::now)
                    .toLocalDate().atTime(0,0,0);
        } else {
            beginDate = dateRange.get(0);
            endDate = dateRange.get(1);
        }
        return Arrays.asList(beginDate, endDate);
    }

    /**
     * 查询日期范围内的排产计划
     *
     * @param query 查询条件
     * @param fillDay 日期范围填充天数
     * @return 日期范围内的排产计划
     */
    protected List<TaskDTO> listReportTask(PlanScheduleReportDTO query, Integer fillDay) {
        List<LocalDateTime> dateRange = getReportDateRange(query.getDateRange());
        LocalDateTime beginDate = dateRange.get(0);
        LocalDateTime endDate = dateRange.get(1);
        if (ObjectNull.isNotNull(endDate) && beginDate.isAfter(endDate)) {
            throw new BusinessException("开始日期不能晚于结束日期");
        }
        List<TaskDTO> tasks = taskAdjustFacadeService.listTasksByDateRange(beginDate, endDate)
                .stream()
                .sorted(Comparator.comparing(TaskDTO::getStartTime))
                .toList();

        if (ObjectNull.isNull(endDate)) {
            endDate = tasks.stream()
                    .map(TaskDTO::getEndTime)
                    .max(Comparator.naturalOrder())
                    .map(time -> time.toLocalDate().atTime(23,59,59))
                    .orElseGet(() -> LocalDate.now().atTime(23,59,59));
            dateRange.set(1, endDate);
        }
        query.setDateRange(changeDateRange(dateRange, fillDay));
        return tasks;
    }


    /**
     * 解析任务，得到返回给前端的显示范围
     *
     * @param tasks 任务
     * @param fillDay 日期范围填充天数
     * @return 日期范围
     */
    protected List<LocalDateTime> parseReportDateRange(List<TaskDTO> tasks, Integer fillDay) {
        LocalDateTime minStartTime = tasks.stream()
                .map(TaskDTO::getStartTime)
                .min(Comparator.naturalOrder())
                .map(time -> time.toLocalDate().atTime(0,0,0))
                .orElseGet(() -> LocalDate.now().atTime(0,0,0));
        LocalDateTime maxStartTime = tasks.stream()
                .map(TaskDTO::getEndTime)
                .max(Comparator.naturalOrder())
                .map(time -> time.toLocalDate().atTime(23,59,59))
                .orElseGet(() -> LocalDate.now().atTime(23,59,59));
        return changeDateRange(Arrays.asList(minStartTime, maxStartTime), fillDay);
    }


    /**
     * 变更日期范围
     *
     * @param dateRange 日期范围
     * @param fillDay 日期范围填充天数
     * @return 日期范围
     */
    private static List<LocalDateTime> changeDateRange(List<LocalDateTime> dateRange, Integer fillDay) {
        // 起始时间往前加8个小时
        LocalDateTime beginDate = dateRange.get(0).minusHours(8);
        LocalDateTime endDate = dateRange.get(1);
        long day =ChronoUnit.DAYS.between(beginDate.toLocalDate(), endDate.toLocalDate());
        // 日期范围默认显示5天，若不足则补齐
        if (day < fillDay) {
            endDate = endDate.plusDays(fillDay - day);
        }
        return Arrays.asList(beginDate, endDate);
    }


    /**
     * 查询任务订单
     *
     * @param tasks 任务集合
     * @return 订单集合
     */
    protected List<PlanTaskOrderPO> listTaskOrder(List<TaskDTO> tasks) {
        if (ObjectNull.isNull(tasks)) {
            return Collections.emptyList();
        }
        Set<String> orderIds = getOrderIds(tasks);
        return planTaskOrderService.listByOrderIds(orderIds);
    }

    /**
     * 查询预览的任务订单
     *
     * @param tasks 任务集合
     * @return 订单集合
     */
    protected List<PlanTaskOrderPO> listPreviewTaskOrder(List<TaskDTO> tasks) {
        if (ObjectNull.isNull(tasks)) {
            return Collections.emptyList();
        }
        Set<String> orderIds = getOrderIds(tasks);
        return planTaskOrderPendingService.list(Wrappers.<PlanTaskOrderPendingPO>lambdaQuery()
                        .in(PlanTaskOrderPendingPO::getOrderId, orderIds))
                .stream()
                .map(taskOrder -> BeanCopyUtil.copy(taskOrder, PlanTaskOrderPO.class))
                .toList();
    }


    /**
     * 提取任务订单id
     *
     * @param tasks 任务集合
     * @return 任务订单id集合
     */
    private Set<String> getOrderIds(List<TaskDTO> tasks) {
        return tasks.stream()
                .map(TaskDTO::getProductionOrderId)
                .filter(ObjectNull::isNotNull)
                .collect(Collectors.toSet());
    }
}
