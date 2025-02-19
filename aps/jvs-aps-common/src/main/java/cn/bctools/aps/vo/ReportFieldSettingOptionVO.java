package cn.bctools.aps.vo;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

/**
 * @author jvs
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@ApiModel("任务可视化字段设置——可选字段")
public class ReportFieldSettingOptionVO {

    @ApiModelProperty("字段key")
    private String fieldKey;

    @ApiModelProperty("字段名")
    private String fieldName;

}
