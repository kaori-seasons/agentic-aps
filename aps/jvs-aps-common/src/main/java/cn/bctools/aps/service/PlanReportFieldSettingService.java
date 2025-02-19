package cn.bctools.aps.service;

import cn.bctools.aps.dto.SaveReportFieldSettingDTO;
import cn.bctools.aps.entity.PlanReportFieldSettingPO;
import cn.bctools.aps.entity.enums.GanttFieldEnum;
import cn.bctools.aps.entity.enums.ReportTypeEnum;
import cn.bctools.aps.vo.DetailReportFieldSettingVO;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;

/**
 * @author jvs
 * 任务可视化字段设置
 */
public interface PlanReportFieldSettingService extends IService<PlanReportFieldSettingPO> {

    /**
     * 保存报告字段设置
     *
     * @param setting 字段设置
     */
    void saveSetting(SaveReportFieldSettingDTO setting);

    /**
     * 获取报告字段设置
     *
     * @param reportType           报告类型
     * @param defaultTaskBarField  默认甘特图任务条默认显示字段
     * @param defaultTooltipFields 默认甘特图任务条提示框默认显示字段
     * @return 字段配置
     */
    DetailReportFieldSettingVO getReportFieldSettingDetail(ReportTypeEnum reportType, List<GanttFieldEnum> defaultTaskBarField, List<GanttFieldEnum> defaultTooltipFields);
}
