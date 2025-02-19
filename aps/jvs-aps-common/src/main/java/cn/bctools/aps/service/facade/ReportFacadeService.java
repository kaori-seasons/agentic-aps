package cn.bctools.aps.service.facade;

import cn.bctools.aps.dto.schedule.PlanScheduleReportDTO;
import cn.bctools.aps.vo.DetailReportFieldSettingVO;

/**
 * @author jvs
 * 报告统一接口
 * <p>
 *     所有报告功能都实现此接口
 */
public interface ReportFacadeService<R, T extends PlanScheduleReportDTO> {

    /**
     * 获取预览报告
     * <p>
     *   临时数据报告（如解析未确认的排产计划，生成的报告）
     * @return 报告
     */
    default R getPreviewReport() {
        return null;
    }

    /**
     * 获取报告
     * <p>
     *     正式数据报告
     * @param query 查询参数
     * @return 报告
     */
    R getReport(T query);

    /**
     * 获取报告字段设置
     *
     * @return 字段配置
     */
    default DetailReportFieldSettingVO getReportFieldSettings() {
        return null;
    }
}
