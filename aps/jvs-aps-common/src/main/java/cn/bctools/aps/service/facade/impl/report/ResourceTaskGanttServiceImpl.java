package cn.bctools.aps.service.facade.impl.report;

import cn.bctools.aps.annotation.Report;
import cn.bctools.aps.dto.schedule.TaskDTO;
import cn.bctools.aps.dto.schedule.report.PlanResourceTaskGanttDTO;
import cn.bctools.aps.entity.PlanInfoPO;
import cn.bctools.aps.entity.PlanTaskOrderPO;
import cn.bctools.aps.entity.ProductionResourcePO;
import cn.bctools.aps.entity.enums.GanttFieldEnum;
import cn.bctools.aps.entity.enums.ReportTypeEnum;
import cn.bctools.aps.service.PlanInfoService;
import cn.bctools.aps.service.PlanReportFieldSettingService;
import cn.bctools.aps.service.ProductionResourceService;
import cn.bctools.aps.service.facade.ReportFacadeService;
import cn.bctools.aps.service.facade.WorkCalendarFacadeService;
import cn.bctools.aps.solve.model.WorkCalendar;
import cn.bctools.aps.util.PlanUtils;
import cn.bctools.aps.vo.DetailReportFieldSettingVO;
import cn.bctools.aps.vo.schedule.report.GanttResourceVO;
import cn.bctools.aps.vo.schedule.report.PlanResourceTaskGanttVO;
import cn.bctools.aps.vo.schedule.report.ResourceGanttTaskVO;
import cn.bctools.common.utils.BeanCopyUtil;
import cn.bctools.common.utils.ObjectNull;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * @author jvs
 * 资源任务甘特图
 * <p>
 *     展示主资源维度任务安排
 */
@Report(type = ReportTypeEnum.PLAN_RESOURCE_TASK_GANTT)
@Slf4j
@Service
@AllArgsConstructor
public class ResourceTaskGanttServiceImpl extends AbstractPlanReport implements ReportFacadeService<PlanResourceTaskGanttVO, PlanResourceTaskGanttDTO> {

    private final ProductionResourceService productionResourceService;
    private final PlanReportFieldSettingService planReportFieldSettingService;
    private final WorkCalendarFacadeService workCalendarFacadeService;
    private final PlanInfoService planInfoService;

    /**
     * 甘特图任务条默认显示字段
     */
    private static final List<GanttFieldEnum> DEFAULT_TASK_BAR_FIELD_ENUMS = Stream.of(GanttFieldEnum.CODE).toList();

    /**
     * 甘特图填充天数
     */
    private static final Integer FILL_DAY = 5;

    /**
     * 甘特图任务条提示框默认显示字段(未配置显示那些字段时，默认的显示字段)
     */
    private static final List<GanttFieldEnum> DEFAULT_TOOLTIP_FIELDS_ENUMS = Stream.of(
            GanttFieldEnum.ORDER_CODE,
            GanttFieldEnum.CODE,
            GanttFieldEnum.MATERIAL_NAME,
            GanttFieldEnum.SCHEDULED_QUANTITY,
            GanttFieldEnum.START_TIME,
            GanttFieldEnum.END_TIME
    ).toList();

    @Override
    public PlanResourceTaskGanttVO getPreviewReport() {
        List<TaskDTO> tasks = listPreviewReportTask();
        List<PlanTaskOrderPO> planTaskOrderList = listPreviewTaskOrder(tasks);

        PlanResourceTaskGanttDTO query = new PlanResourceTaskGanttDTO();
        query.setDateRange(parseReportDateRange(tasks, FILL_DAY));
        return generateReport(query, tasks, planTaskOrderList);
    }

    @Override
    public PlanResourceTaskGanttVO getReport(PlanResourceTaskGanttDTO query) {
        List<TaskDTO> tasks = listReportTask(query, FILL_DAY);
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
    private PlanResourceTaskGanttVO generateReport(PlanResourceTaskGanttDTO query, List<TaskDTO> tasks, List<PlanTaskOrderPO> planTaskOrderList) {
        // 获取主资源
        List<ProductionResourcePO> resources = productionResourceService.list(Wrappers.<ProductionResourcePO>lambdaQuery()
                        .eq(ObjectNull.isNotNull(query.getResourceGroup()), ProductionResourcePO::getResourceGroup, query.getResourceGroup()));
        if (ObjectNull.isNull(resources)) {
            return new PlanResourceTaskGanttVO();
        }
        resources.sort(Comparator.comparing(ProductionResourcePO::getResourceGroup));
        List<GanttResourceVO> ganttResourceList = BeanCopyUtil.copys(resources, GanttResourceVO.class);

        // 甘特任务信息
        List<ResourceGanttTaskVO> ganttTaskList = null;
        if (ObjectNull.isNotNull(tasks)) {
            tasks = tasks.stream().filter(task -> !task.getDiscard()).toList();
            // 获取资源可用日历
            Set<String> resourceIds = resources.stream().map(ProductionResourcePO::getId).collect(Collectors.toSet());
            Map<String, List<WorkCalendar>> workCalendarMap = workCalendarFacadeService.listResourceScheduleCalendar(resourceIds);
            DetailReportFieldSettingVO detailReportFieldSetting = getReportFieldSettings();
            // 转换为甘特任务集合
            ganttTaskList = PlanUtils.convertGanttTaskList(tasks, detailReportFieldSetting, resources, planTaskOrderList, workCalendarMap);
        }

        PlanInfoPO planInfo = Optional.ofNullable(planInfoService.getConfirmedPlan()).orElseGet(PlanInfoPO::new);
        PlanResourceTaskGanttVO vo = new PlanResourceTaskGanttVO()
                .setEarliestTaskStartTime(planInfo.getEarliestTaskStartTime())
                .setLastTaskAssignmentTime(planInfo.getLastTaskAssignmentTime())
                .setResources(ganttResourceList)
                .setTasks(ganttTaskList);
        vo.setDateRange(query.getDateRange());
        return vo;
    }

    @Override
    public DetailReportFieldSettingVO getReportFieldSettings() {
        return planReportFieldSettingService.getReportFieldSettingDetail(ReportTypeEnum.PLAN_RESOURCE_TASK_GANTT, DEFAULT_TASK_BAR_FIELD_ENUMS, DEFAULT_TOOLTIP_FIELDS_ENUMS);
    }
}
