package cn.bctools.aps.dto;

import cn.bctools.aps.entity.dto.ReportFieldDTO;
import cn.bctools.aps.entity.enums.ReportTypeEnum;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.experimental.Accessors;

import javax.validation.constraints.NotNull;
import java.util.List;

/**
 * @author jvs
 */
@Data
@Accessors(chain = true)
@ApiModel("保存任务可视化字段设置")
public class SaveReportFieldSettingDTO {

    @ApiModelProperty(value = "报告类型", required = true)
    @NotNull(message = "类型不能为空")
    private ReportTypeEnum reportType;

    @ApiModelProperty("任务条显示字段")
    private List<ReportFieldDTO> taskBarFields;

    @ApiModelProperty("任务条提示框显示字段")
    private List<ReportFieldDTO> tooltipFields;


}
