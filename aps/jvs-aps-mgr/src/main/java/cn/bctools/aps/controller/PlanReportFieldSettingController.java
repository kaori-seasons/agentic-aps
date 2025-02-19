package cn.bctools.aps.controller;

import cn.bctools.aps.component.ReportHandler;
import cn.bctools.aps.dto.SaveReportFieldSettingDTO;
import cn.bctools.aps.entity.enums.GanttFieldEnum;
import cn.bctools.aps.entity.enums.ReportTypeEnum;
import cn.bctools.aps.service.PlanReportFieldSettingService;
import cn.bctools.aps.vo.DetailReportFieldSettingVO;
import cn.bctools.aps.vo.ReportFieldSettingOptionVO;
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
 *
 */
@Api(tags = "[生产计划]任务可视化字段设置")
@RestController
@RequestMapping("/plan-report-field-setting")
@AllArgsConstructor
public class PlanReportFieldSettingController {

    private final PlanReportFieldSettingService service;
    private final ReportHandler reportHandler;

    @ApiOperation("获取可配置字段")
    @ApiImplicitParams({
            @ApiImplicitParam(name = "reportType", value = "报告类型", required = true)
    })
    @GetMapping("/field/options/{reportType}")
    public R<List<ReportFieldSettingOptionVO>> listFieldOptions(@PathVariable ReportTypeEnum reportType) {
        List<ReportFieldSettingOptionVO> options = GanttFieldEnum.listFieldOptionsByReportType(reportType)
                .stream()
                .map(field -> new ReportFieldSettingOptionVO()
                        .setFieldKey(field.getFieldKey())
                        .setFieldName(field.getFieldName()))
                .toList();
        return R.ok(options);
    }

    @ApiOperation("保存配置")
    @PostMapping
    public R<String> save(@Validated @RequestBody SaveReportFieldSettingDTO reportFieldSetting) {
        service.saveSetting(reportFieldSetting);
        return R.ok();
    }

    @ApiOperation("获取配置")
    @ApiImplicitParams({
            @ApiImplicitParam(name = "reportType", value = "报告类型", required = true)
    })
    @GetMapping("/{reportType}")
    public R<DetailReportFieldSettingVO> detail(@PathVariable ReportTypeEnum reportType) {
        return R.ok(reportHandler.getReportService(reportType).getReportFieldSettings());
    }

}
