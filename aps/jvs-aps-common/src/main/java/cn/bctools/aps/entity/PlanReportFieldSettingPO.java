package cn.bctools.aps.entity;

import cn.bctools.aps.entity.dto.ReportFieldDTO;
import cn.bctools.aps.entity.enums.ReportTypeEnum;
import cn.bctools.aps.entity.handler.ReportFieldTypeHandler;
import cn.bctools.database.entity.po.BasalPo;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * @author jvs
 * 任务可视化字段设置
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Accessors(chain = true)
@TableName(value = "jvs_plan_report_field_setting", autoResultMap = true)
public class PlanReportFieldSettingPO extends BasalPo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.ASSIGN_UUID)
    private String id;

    /**
     * 报告类型
     */
    @TableField(value = "report_type")
    private ReportTypeEnum reportType;

    /**
     * 任务条显示字段
     */
    @TableField(value = "task_bar_fields", typeHandler = ReportFieldTypeHandler.class)
    private List<ReportFieldDTO> taskBarFields;

    /**
     * 任务条提示框显示字段
     */
    @TableField(value = "tooltip_fields", typeHandler = ReportFieldTypeHandler.class)
    private List<ReportFieldDTO> tooltipFields;
}
