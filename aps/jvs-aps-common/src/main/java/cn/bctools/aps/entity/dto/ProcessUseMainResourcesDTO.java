package cn.bctools.aps.entity.dto;

import cn.bctools.aps.annotation.DecimalPlacesMax;
import cn.bctools.aps.annotation.ThroughputFormat;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.experimental.Accessors;

import javax.validation.constraints.DecimalMin;
import javax.validation.constraints.NotBlank;
import java.math.BigDecimal;

/**
 * @author jvs
 */
@Data
@Accessors(chain = true)
@ApiModel("工序——可用的主资源")
public class ProcessUseMainResourcesDTO {
    @ApiModelProperty(value = "资源id", required = true)
    @NotBlank(message = "未选择主资源")
    private String id;

    @ApiModelProperty(value = "容量")
    @DecimalMin(value = "0", message = "容量不能小于0")
    @DecimalPlacesMax(value = 6, message = "容量数量最多6位小数")
    private BigDecimal capacity;

    @ApiModelProperty(value = "产能。 格式如：15D,10DP,20PD")
    @ThroughputFormat(message = "产能格式错误")
    private String throughput;
}
