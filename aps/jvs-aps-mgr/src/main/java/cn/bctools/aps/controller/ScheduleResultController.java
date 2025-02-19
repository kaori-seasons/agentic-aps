package cn.bctools.aps.controller;

import cn.bctools.aps.component.ReportHandler;
import cn.bctools.aps.dto.schedule.report.PlanMaterialRequirementGanttDTO;
import cn.bctools.aps.dto.schedule.report.PlanOrderTaskGanttDTO;
import cn.bctools.aps.dto.schedule.report.PlanResourceTaskGanttDTO;
import cn.bctools.aps.dto.schedule.report.PlanTaskReportDTO;
import cn.bctools.aps.entity.enums.ReportTypeEnum;
import cn.bctools.aps.service.facade.PlanSolutionFacadeService;
import cn.bctools.aps.vo.schedule.report.*;
import cn.bctools.common.utils.R;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiImplicitParam;
import io.swagger.annotations.ApiImplicitParams;
import io.swagger.annotations.ApiOperation;
import lombok.AllArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * @author jvs
 */
@Api(tags = "[生产计划]排产结果展示")
@RestController
@RequestMapping("/schedule-result/report")
@AllArgsConstructor
public class ScheduleResultController {

    private final ReportHandler reportHandler;
    private final PlanSolutionFacadeService planSolutionFacadeService;

    @ApiOperation("工作任务表")
    @PostMapping("/plan/tasks")
    public R<List<PlanTaskReportVO>> previewTaskList(@Validated @RequestBody PlanTaskReportDTO pageQuery) {
        return R.ok(reportHandler.generateReport(ReportTypeEnum.PLAN_TASK_LIST, pageQuery));
    }

    @ApiOperation("资源甘特图")
    @PostMapping("/plan/resource/gantt")
    public R<PlanResourceTaskGanttVO> previewResourceGantt(@Validated @RequestBody PlanResourceTaskGanttDTO planResourceTaskGantt) {
        return R.ok(reportHandler.generateReport(ReportTypeEnum.PLAN_RESOURCE_TASK_GANTT, planResourceTaskGantt));
    }

    @ApiOperation("订单甘特图")
    @PostMapping("/plan/order/gantt")
    public R<PlanOrderTaskGanttVO> previewOrderGantt(@Validated @RequestBody PlanOrderTaskGanttDTO planOrderTaskGantt) {
        return R.ok(reportHandler.generateReport(ReportTypeEnum.PLAN_ORDER_TASK_GANTT, planOrderTaskGantt));
    }

    @ApiOperation("物料需求甘特图")
    @PostMapping("/plan/material/gantt")
    public R<PlanMaterialRequirementGanttVO> previewMaterialGantt(@Validated @RequestBody PlanMaterialRequirementGanttDTO planMaterialRequirementGantt) {
        return R.ok(reportHandler.generateReport(ReportTypeEnum.PLAN_MATERIAL_REQUIREMENT_GANTT, planMaterialRequirementGantt));
    }

    @ApiOperation("甘特图任务详情")
    @ApiImplicitParams({
            @ApiImplicitParam(name = "code", value = "任务编码", required = true)
    })
    @PostMapping("/plan/task/detail/{code}")
    public R<PlanTaskGanttDetailVO> getResourceGanttTaskDetail(@PathVariable String code) {
        return R.ok(planSolutionFacadeService.getPlanTaskGanttDetail(code));
    }

}
