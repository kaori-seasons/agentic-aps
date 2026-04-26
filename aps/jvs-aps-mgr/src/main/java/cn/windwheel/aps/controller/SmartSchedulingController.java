package cn.windwheel.aps.controller;

import cn.windwheel.aps.component.ReportHandler;
import cn.windwheel.aps.dto.GeneratePlanningSmartDTO;
import cn.windwheel.aps.entity.enums.ReportTypeEnum;
import cn.windwheel.aps.service.facade.PlanningSmartService;
import cn.windwheel.aps.vo.schedule.PlanProgressVO;
import cn.windwheel.aps.vo.schedule.report.PlanMaterialRequirementGanttVO;
import cn.windwheel.aps.vo.schedule.report.PlanOrderTaskGanttVO;
import cn.windwheel.aps.vo.schedule.report.PlanResourceTaskGanttVO;
import cn.windwheel.aps.vo.schedule.report.PlanTaskReportVO;
import cn.windwheel.common.annotation.AuditOperation;
import cn.windwheel.common.annotation.RequirePermission;
import cn.windwheel.common.utils.R;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.AllArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * @author jvs
 */
@Api(tags = "[生产计划]智能排产")
@RestController
@RequestMapping("/smart-scheduling")
@AllArgsConstructor
@RequirePermission("aps:schedule:view")
public class SmartSchedulingController {

    private final PlanningSmartService planningSmartService;
    private final ReportHandler reportHandler;

    @ApiOperation("生成排产计划")
    @PostMapping("/generate")
    @RequirePermission("aps:schedule:run")
    @AuditOperation("生成排产计划")
    public R<String> generate(@Validated @RequestBody GeneratePlanningSmartDTO generatePlanningSmart) {
        planningSmartService.generate(generatePlanningSmart);
        return R.ok();
    }

    @ApiOperation("获取排产进度")
    @GetMapping("/plan/progress")
    public R<PlanProgressVO> getPlanProgress() {
        return R.ok(planningSmartService.getPlanProgress());
    }

    @ApiOperation("放弃排产计划")
    @PostMapping("/plan/pending/cancel")
    @RequirePermission("aps:schedule:cancel")
    @AuditOperation("放弃待确认排产计划")
    public R<String> cancel() {
        planningSmartService.cancelPlanPending();
        return R.ok();
    }

    @ApiOperation("确认排产计划")
    @PostMapping("/plan/pending/confirm")
    @RequirePermission("aps:schedule:confirm")
    @AuditOperation("确认排产计划")
    public R<String> confirm() {
        planningSmartService.confirmPlan();
        return R.ok();
    }

    @ApiOperation("预览工作任务表")
    @PostMapping("/preview/plan/tasks")
    @RequirePermission("aps:schedule:preview")
    public R<List<PlanTaskReportVO>> previewTaskList() {
        return R.ok(reportHandler.generatePreviewReport(ReportTypeEnum.PLAN_TASK_LIST));
    }

    @ApiOperation("预览资源甘特图")
    @PostMapping("/preview/plan/resource/gantt")
    @RequirePermission("aps:schedule:preview")
    public R<PlanResourceTaskGanttVO> previewResourceGantt() {
        return R.ok(reportHandler.generatePreviewReport(ReportTypeEnum.PLAN_RESOURCE_TASK_GANTT));
    }

    @ApiOperation("预览订单甘特图")
    @PostMapping("/preview/plan/order/gantt")
    @RequirePermission("aps:schedule:preview")
    public R<PlanOrderTaskGanttVO> previewOrderGantt() {
        return R.ok(reportHandler.generatePreviewReport(ReportTypeEnum.PLAN_ORDER_TASK_GANTT));
    }

    @ApiOperation("预览物料需求甘特图")
    @PostMapping("/preview/plan/material/gantt")
    @RequirePermission("aps:schedule:preview")
    public R<PlanMaterialRequirementGanttVO> previewMaterialGantt() {
        return R.ok(reportHandler.generatePreviewReport(ReportTypeEnum.PLAN_MATERIAL_REQUIREMENT_GANTT));
    }
}
