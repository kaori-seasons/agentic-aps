package cn.bctools.aps.entity.dto;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * @author jvs
 */
@Data
@Accessors(chain = true)
@ApiModel("任务可视化字段设置")
public class ReportFieldDTO {

    @ApiModelProperty("字段key")
    private String fieldKey;

    @ApiModelProperty("字段名")
    private String fieldName;
}
