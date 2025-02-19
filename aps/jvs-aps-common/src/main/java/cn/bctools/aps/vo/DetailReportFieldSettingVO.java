package cn.bctools.aps.vo;

import cn.bctools.aps.entity.dto.ReportFieldDTO;
import cn.bctools.aps.entity.enums.ReportTypeEnum;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * @author jvs
 */
@Data
@Accessors(chain = true)
@ApiModel("任务可视化字段设置")
public class DetailReportFieldSettingVO {

    @ApiModelProperty(value = "报告类型")
    private ReportTypeEnum reportType;

    @ApiModelProperty("任务条显示字段")
    private List<ReportFieldDTO> taskBarFields;

    @ApiModelProperty("任务条提示框显示字段")
    private List<ReportFieldDTO> tooltipFields;
}
