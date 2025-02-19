package cn.bctools.aps.service.facade.impl.report;

import cn.bctools.aps.annotation.Report;
import cn.bctools.aps.dto.schedule.TaskDTO;
import cn.bctools.aps.dto.schedule.report.PlanTaskReportDTO;
import cn.bctools.aps.entity.PlanTaskOrderPO;
import cn.bctools.aps.entity.ProductionResourcePO;
import cn.bctools.aps.entity.enums.ReportTypeEnum;
import cn.bctools.aps.service.ProductionResourceService;
import cn.bctools.aps.service.facade.ReportFacadeService;
import cn.bctools.aps.util.BigDecimalUtils;
import cn.bctools.aps.util.PlanUtils;
import cn.bctools.aps.vo.schedule.report.PlanTaskReportVO;
import cn.bctools.common.utils.BeanCopyUtil;
import cn.bctools.common.utils.ObjectNull;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * @author jvs
 * 工作任务表
 * <p>
 *    已排产的任务集合
 */
@Report(type = ReportTypeEnum.PLAN_TASK_LIST)
@Slf4j
@Service
@AllArgsConstructor
public class PlanTaskReportServiceImpl extends AbstractPlanReport implements ReportFacadeService<List<PlanTaskReportVO>, PlanTaskReportDTO> {

    private final ProductionResourceService productionResourceService;

    /**
     * 甘特图填充天数
     */
    private static final Integer FILL_DAY = 0;

    @Override
    public List<PlanTaskReportVO> getPreviewReport() {
        List<TaskDTO> tasks = listPreviewReportTask();
        if (ObjectNull.isNull(tasks)) {
            return Collections.emptyList();
        }
        List<PlanTaskOrderPO> planTaskOrderList = listPreviewTaskOrder(tasks);
        return generateReport(tasks, planTaskOrderList);
    }

    @Override
    public List<PlanTaskReportVO> getReport(PlanTaskReportDTO query) {
        List<TaskDTO> allTasks = listReportTask(query, FILL_DAY);
        // 若有筛选条件，则根据条件筛选任务
        List<TaskDTO> tasks = allTasks.stream()
                .filter(task -> {
                    if (ObjectNull.isNull(query.getOrderId())) {
                        return true;
                    } else {
                        return query.getOrderId().equals(task.getMainOrderId())
                                || query.getOrderId().equals(task.getProductionOrderId());
                    }
                })
                .filter(task -> {
                    if (ObjectNull.isNull(query.getTaskCode())) {
                        return true;
                    } else {
                        return task.getCode().equals(query.getTaskCode());
                    }
                })
                .sorted(Comparator.comparing(TaskDTO::getStartTime))
                .toList();
        if (ObjectNull.isNull(tasks)) {
            return Collections.emptyList();
        }

        List<PlanTaskOrderPO> planTaskOrderList = listTaskOrder(tasks);
        return generateReport(tasks, planTaskOrderList);
    }

    /**
     * 生成报告
     *
     * @param tasks             任务集合
     * @param planTaskOrderList 任务订单集合
     * @return 任务报告集合
     */
    private List<PlanTaskReportVO> generateReport(List<TaskDTO> tasks, List<PlanTaskOrderPO> planTaskOrderList) {
        // 获取主资源
        Map<String, ProductionResourcePO> resourcesMap = productionResourceService.list().stream()
                .collect(Collectors.toMap(ProductionResourcePO::getId, Function.identity()));
        if (ObjectNull.isNull(resourcesMap)) {
            return Collections.emptyList();
        }
        // 订单信息
        Map<String, PlanTaskOrderPO> planTaskOrderMap = planTaskOrderList.stream().collect(Collectors.toMap(PlanTaskOrderPO::getOrderId, Function.identity()));
        // 生成报告
        LocalDateTime now = LocalDateTime.now();
        return tasks.stream()
                .filter(task -> ObjectNull.isNull(task.getMergeTaskCode()))
                .filter(task -> !task.getDiscard())
                .map(task -> {
                    PlanTaskReportVO report = BeanCopyUtil.copy(task, PlanTaskReportVO.class);
                    report.setScheduledQuantity(BigDecimalUtils.stripTrailingZeros(report.getScheduledQuantity()));
                    // 设置订单相关回显信息
                    if (task.getMergeTask()) {
                        List<PlanTaskOrderPO> childOrders = tasks.stream()
                                .filter(t -> task.getCode().equals(t.getMergeTaskCode()))
                                .map(TaskDTO::getProductionOrderId)
                                .distinct()
                                .map(planTaskOrderMap::get)
                                .toList();
                        report.setOrderCode(childOrders.stream().map(order -> order.getOrderInfo().getCode()).collect(Collectors.joining(";")))
                                .setMaterialName(childOrders.get(0).getOrderMaterialInfo().getName());
                    } else {
                        PlanTaskOrderPO taskOrder = planTaskOrderMap.get(task.getProductionOrderId());
                        report.setOrderCode(taskOrder.getOrderInfo().getCode())
                                .setMaterialName(taskOrder.getOrderMaterialInfo().getName());
                    }
                    report.setProcessCode(task.getProcessInfo().getCode())
                            .setProcessName(task.getProcessInfo().getName());
                    report.setMainResourceName(resourcesMap.get(task.getMainResourceId()).getName());
                    report.setOverdueTimeString(PlanUtils.formatOverdueTimeString(task, now));
                    return report;
                })
                .collect(Collectors.toList());
    }

}
