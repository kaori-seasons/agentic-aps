package cn.windwheel.aps.service.impl;

import cn.windwheel.aps.dto.SaveReportFieldSettingDTO;
import cn.windwheel.aps.entity.PlanReportFieldSettingPO;
import cn.windwheel.aps.entity.dto.ReportFieldDTO;
import cn.windwheel.aps.entity.enums.GanttFieldEnum;
import cn.windwheel.aps.entity.enums.ReportTypeEnum;
import cn.windwheel.aps.mapper.PlanReportFieldSettingMapper;
import cn.windwheel.aps.service.PlanReportFieldSettingService;
import cn.windwheel.aps.vo.DetailReportFieldSettingVO;
import cn.windwheel.common.exception.BusinessException;
import cn.windwheel.common.utils.BeanCopyUtil;
import cn.windwheel.common.utils.ObjectNull;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * @author jvs
 */
@Service
public class PlanReportFieldSettingServiceImpl extends ServiceImpl<PlanReportFieldSettingMapper, PlanReportFieldSettingPO> implements PlanReportFieldSettingService {

    @Transactional(rollbackFor = Exception.class)
    @Override
    public void saveSetting(SaveReportFieldSettingDTO setting) {
        // 校验是否可以保存
        checkCanSave(setting);

        // 查询已有设置
        PlanReportFieldSettingPO fieldSetting = Optional.ofNullable(getOne(Wrappers.<PlanReportFieldSettingPO>lambdaQuery()
                .eq(PlanReportFieldSettingPO::getReportType, setting.getReportType())))
                .orElseGet(PlanReportFieldSettingPO::new);

        // 填充设计
        fieldSetting.setReportType(setting.getReportType());
        fieldSetting.setTaskBarFields(setting.getTaskBarFields());
        fieldSetting.setTooltipFields(setting.getTooltipFields());

        // 保存
        if (ObjectNull.isNull(fieldSetting.getId())) {
            save(fieldSetting);
        } else {
            updateById(fieldSetting);
        }
    }

    @Override
    public DetailReportFieldSettingVO getReportFieldSettingDetail(ReportTypeEnum reportType, List<GanttFieldEnum> defaultTaskBarField, List<GanttFieldEnum> defaultTooltipFields) {
        PlanReportFieldSettingPO fieldSetting = getOne(Wrappers.<PlanReportFieldSettingPO>lambdaQuery()
                .eq(PlanReportFieldSettingPO::getReportType, reportType));
        if (ObjectNull.isNull(fieldSetting)) {
            fieldSetting = new PlanReportFieldSettingPO();
            fieldSetting.setReportType(reportType);
        }
        // 任务条显示字段配置为空时，设置默认字段
        if (ObjectNull.isNull(fieldSetting.getTaskBarFields())) {
            List<ReportFieldDTO> taskBarFields = defaultTaskBarField.stream()
                    .map(field -> new ReportFieldDTO()
                            .setFieldKey(field.getFieldKey())
                            .setFieldName(field.getFieldName()))
                    .toList();
            fieldSetting.setTaskBarFields(taskBarFields);
        }
        // 任务条提示框显示字段配置为空时，设置默认字段
        if (ObjectNull.isNull(fieldSetting.getTooltipFields())) {
            List<ReportFieldDTO> tooltipFields = defaultTooltipFields.stream()
                    .map(field -> new ReportFieldDTO()
                            .setFieldKey(field.getFieldKey())
                            .setFieldName(field.getFieldName()))
                    .toList();
            fieldSetting.setTooltipFields(tooltipFields);
        }
        return BeanCopyUtil.copy(fieldSetting, DetailReportFieldSettingVO.class);
    }


    /**
     * 校验是否可以保存（新增/修改）
     *
     * @param setting 字段设置
     */
    private void checkCanSave(SaveReportFieldSettingDTO setting) {
        // 校验逻辑
        Consumer<List<ReportFieldDTO>> checkFieldConsumer = reportFields -> {
            if (ObjectNull.isNull(reportFields)) {
                return;
            }
            long fieldKeySize = reportFields.stream().map(ReportFieldDTO::getFieldKey).distinct().count();
            if (fieldKeySize != reportFields.size()) {
                throw new BusinessException("字段不能重复");
            }
            reportFields.forEach(field -> {
                if (ObjectNull.isNull(GanttFieldEnum.getEnumByFieldKey(field.getFieldKey()))) {
                    throw new BusinessException("不支持的字段", field.getFieldKey());
                }
            });
        };

        // 校验任务条显示字段是否可以保存
        checkFieldConsumer.accept(setting.getTaskBarFields());
        // 校验任务条提示框显示字段是否可以保存
        checkFieldConsumer.accept(setting.getTooltipFields());
    }
}
