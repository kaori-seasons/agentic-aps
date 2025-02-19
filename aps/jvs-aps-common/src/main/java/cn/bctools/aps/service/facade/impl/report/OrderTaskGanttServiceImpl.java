package cn.bctools.aps.service.facade.impl.report;

import cn.bctools.aps.annotation.Report;
import cn.bctools.aps.dto.schedule.TaskDTO;
import cn.bctools.aps.dto.schedule.report.PlanOrderTaskGanttDTO;
import cn.bctools.aps.entity.PlanTaskOrderPO;
import cn.bctools.aps.entity.enums.GanttFieldEnum;
import cn.bctools.aps.entity.enums.ReportTypeEnum;
import cn.bctools.aps.service.PlanReportFieldSettingService;
import cn.bctools.aps.service.facade.ReportFacadeService;
import cn.bctools.aps.util.PlanUtils;
import cn.bctools.aps.vo.DetailReportFieldSettingVO;
import cn.bctools.aps.vo.schedule.report.GanttOrderVO;
import cn.bctools.aps.vo.schedule.report.OrderGanttTaskVO;
import cn.bctools.aps.vo.schedule.report.PlanOrderTaskGanttVO;
import cn.bctools.common.utils.BeanCopyUtil;
import cn.bctools.common.utils.ObjectNull;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * @author jvs
 * 订单任务甘特图
 * <p>
 *     展示订单维度任务安排
 */
@Report(type = ReportTypeEnum.PLAN_ORDER_TASK_GANTT)
@Slf4j
@Service
@AllArgsConstructor
public class OrderTaskGanttServiceImpl extends AbstractPlanReport implements ReportFacadeService<PlanOrderTaskGanttVO, PlanOrderTaskGanttDTO> {

    private final PlanReportFieldSettingService planReportFieldSettingService;

    /**
     * 甘特图任务条默认显示字段
     */
    private static final List<GanttFieldEnum> DEFAULT_TASK_BAR_FIELD_ENUMS = Stream.of(GanttFieldEnum.PROCESS_CODE).toList();

    /**
     * 甘特图填充天数
     */
    private static final Integer FILL_DAY = 5;

    /**
     * 甘特图任务条提示框默认显示字段
     */
    private static final List<GanttFieldEnum> DEFAULT_TOOLTIP_FIELDS_ENUMS = Stream.of(
            GanttFieldEnum.PROCESS_CODE,
            GanttFieldEnum.MATERIAL_NAME,
            GanttFieldEnum.SCHEDULED_QUANTITY,
            GanttFieldEnum.START_TIME,
            GanttFieldEnum.END_TIME
    ).toList();

    @Override
    public PlanOrderTaskGanttVO getPreviewReport() {
        List<TaskDTO> tasks = listPreviewReportTask();
        if (ObjectNull.isNull(tasks)) {
            return new PlanOrderTaskGanttVO();
        }
        List<PlanTaskOrderPO> planTaskOrderList = listPreviewTaskOrder(tasks);
        PlanOrderTaskGanttDTO query = new PlanOrderTaskGanttDTO();
        query.setDateRange(parseReportDateRange(tasks, FILL_DAY));
        return generateReport(query, tasks, planTaskOrderList);
    }

    @Override
    public PlanOrderTaskGanttVO getReport(PlanOrderTaskGanttDTO query) {
        List<TaskDTO> allTasks = listReportTask(query, FILL_DAY);
        // 若有订单id，则筛选指定订单id相关的任务
        List<TaskDTO> tasks = ObjectNull.isNull(query.getOrderId()) ? allTasks : allTasks.stream()
                .filter(task -> query.getOrderId().equals(task.getMainOrderId())
                        || query.getOrderId().equals(task.getProductionOrderId()))
                .collect(Collectors.toList());
        List<PlanTaskOrderPO> planTaskOrderList = listTaskOrder(tasks);
        return generateReport(query, tasks, planTaskOrderList);
    }

    /**
     * 生成报告
     *
     * @param tasks         任务集合
     * @param planTaskOrderList 任务订单集合
     * @return 任务报告集合
     */
    private PlanOrderTaskGanttVO generateReport(PlanOrderTaskGanttDTO query, List<TaskDTO> tasks, List<PlanTaskOrderPO> planTaskOrderList) {
        Map<String, List<TaskDTO>> groupOrderProcessTaskMap = tasks.stream()
                .filter(task -> !task.getMergeTask())
                .collect(Collectors.groupingBy(task -> task.getProductionOrderId() + "-" + task.getProcessInfo().getCode()));

        // 解析任务为订单甘特图显示的任务
        DetailReportFieldSettingVO fieldSetting = getReportFieldSettings();
        Map<String, PlanTaskOrderPO> planTaskOrderMap = planTaskOrderList.stream()
                .collect(Collectors.toMap(PlanTaskOrderPO::getOrderId, Function.identity()));
        LocalDateTime now = LocalDateTime.now();
        List<OrderGanttTaskVO> ganttTaskList = groupOrderProcessTaskMap.values().stream()
                .map(orderProcessTasks -> {
                    TaskDTO newTask = orderProcessTasks.get(0);
                    if (orderProcessTasks.size() > 1) {
                        // 将多个工序任务合并为一个任务展示
                        // 多任务计划生产数量之和
                        BigDecimal scheduledQuantity = BigDecimal.ZERO;
                        // 多任务计划总已生产数量之和
                        BigDecimal quantityCompleted = BigDecimal.ZERO;
                        // 开始时间
                        LocalDateTime startTime = newTask.getStartTime();
                        // 结束时间
                        LocalDateTime endTime = newTask.getEndTime();
                        // 计算合并后的数据
                        for (TaskDTO task : orderProcessTasks) {
                            scheduledQuantity = scheduledQuantity.add(task.getScheduledQuantity());
                            quantityCompleted = quantityCompleted.add(task.getQuantityCompleted());
                            if (task.getStartTime().isBefore(startTime)) {
                                startTime = task.getStartTime();
                            }
                            if (task.getEndTime().isAfter(endTime)) {
                                endTime = task.getEndTime();
                            }
                        }
                        newTask.setScheduledQuantity(scheduledQuantity)
                                .setStartTime(startTime)
                                .setEndTime(endTime)
                                .setQuantityCompleted(quantityCompleted)
                                .setTaskStatus(PlanUtils.calculateTaskStatus(newTask.getScheduledQuantity(), newTask.getQuantityCompleted()));
                    }

                    return BeanCopyUtil.copy(newTask, OrderGanttTaskVO.class)
                            .setProcessCode(newTask.getProcessInfo().getCode())
                            .setExtras(PlanUtils.fetchDynamicFields(newTask, fieldSetting, planTaskOrderMap))
                            .setOverdueTimeString(PlanUtils.formatOverdueTimeString(newTask, now))
                            .setDelayTimeString(planTaskOrderMap.get(newTask.getProductionOrderId()).getDelayTimeString());
                })
                .sorted(Comparator.comparing(OrderGanttTaskVO::getStartTime))
                .toList();

        // 订单信息按预计交付时间顺序排序
        List<GanttOrderVO> ganttOrders = planTaskOrderList.stream()
                .sorted(Comparator.comparing(order -> order.getOrderInfo().getDeliveryTime()))
                .map(order -> new GanttOrderVO()
                        .setId(order.getOrderId())
                        .setCode(order.getOrderInfo().getCode()))
                .toList();
        PlanOrderTaskGanttVO vo = new PlanOrderTaskGanttVO()
                .setOrders(ganttOrders)
                .setTasks(ganttTaskList);
        vo.setDateRange(query.getDateRange());
        return vo;
    }

    @Override
    public DetailReportFieldSettingVO getReportFieldSettings() {
        return planReportFieldSettingService.getReportFieldSettingDetail(ReportTypeEnum.PLAN_ORDER_TASK_GANTT, DEFAULT_TASK_BAR_FIELD_ENUMS, DEFAULT_TOOLTIP_FIELDS_ENUMS);
    }
}
