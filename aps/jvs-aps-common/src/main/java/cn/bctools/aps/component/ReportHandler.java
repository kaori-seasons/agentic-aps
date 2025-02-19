package cn.bctools.aps.component;

import cn.bctools.aps.annotation.Report;
import cn.bctools.aps.dto.schedule.PlanScheduleReportDTO;
import cn.bctools.aps.entity.enums.ReportTypeEnum;
import cn.bctools.aps.service.facade.ReportFacadeService;
import cn.bctools.common.exception.BusinessException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * @author jvs
 * 报表统一处理
 */
@Component
public class ReportHandler {

    private final Map<ReportTypeEnum, ReportFacadeService<?, ? extends PlanScheduleReportDTO>> reportFacadeServiceMap = new EnumMap<>(ReportTypeEnum.class);

    @Autowired
    public ReportHandler(List<ReportFacadeService<?, ? extends PlanScheduleReportDTO>> reportFacadeServiceList) {
        reportFacadeServiceList.forEach(service -> {
            Report reportAnnotation = AnnotationUtils.findAnnotation(service.getClass(), Report.class);
            if (reportAnnotation != null) {
                reportFacadeServiceMap.put(reportAnnotation.type(), service);
            }
        });
    }

    /**
     * 生成预览报告
     *
     * @param reportType 报告类型
     * @return 预览报告
     */
    public <R, T extends PlanScheduleReportDTO> R generatePreviewReport(ReportTypeEnum reportType) {
        ReportFacadeService<R, T> service = getReportService(reportType);
        return service.getPreviewReport();
    }

    /**
     * 生成预览报告
     *
     * @param reportType 报告类型
     * @param input      入参
     * @return 预览报告
     */
    public <R, T extends PlanScheduleReportDTO> R generateReport(ReportTypeEnum reportType, T input) {
        ReportFacadeService<R, T> service = getReportService(reportType);
        return service.getReport(input);
    }

    /**
     * 获取报告服务
     *
     * @param reportType 报告类型
     * @return 报告服务
     */
    @SuppressWarnings("unchecked")
    public <R, T extends PlanScheduleReportDTO> ReportFacadeService<R, T> getReportService(ReportTypeEnum reportType) {
        ReportFacadeService<R, T> service = (ReportFacadeService<R, T>) reportFacadeServiceMap.get(reportType);
        if (service == null) {
            throw new BusinessException("不支持的报表类型", reportType);
        }
        return service;
    }
}
