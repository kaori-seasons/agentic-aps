package cn.bctools.aps.dto;

import cn.bctools.aps.annotation.DecimalPlacesMax;
import cn.bctools.aps.annotation.ThroughputFormat;
import cn.bctools.aps.entity.enums.ResourceStatusEnum;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.experimental.Accessors;
import org.hibernate.validator.constraints.Length;

import javax.validation.constraints.DecimalMin;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.math.BigDecimal;

/**
 * @author jvs
 */
@Data
@Accessors(chain = true)
@ApiModel("新增资源")
public class SaveProductionResourceDTO {
    @ApiModelProperty(value = "名称", required = true)
    @NotBlank(message = "资源名称不能为空")
    private String name;

    @ApiModelProperty(value = "编码", required = true)
    @NotBlank(message = "资源编码不能为空")
    @Length(max = 30, message = "编码最多30个字符")
    private String code;

    @ApiModelProperty(value = "资源组")
    private String resourceGroup;

    @ApiModelProperty(value = "容量")
    @DecimalMin(value = "0", message = "容量不能小于0")
    @DecimalPlacesMax(value = 6, message = "容量最多6位小数")
    private BigDecimal capacity;

    @ApiModelProperty(value = "容量计量单位")
    private String unit;

    @ApiModelProperty(value = "产能。 格式如：15D,10DP,20PD")
    @ThroughputFormat(message = "产能格式错误")
    private String throughput;

    @ApiModelProperty(value = "资源状态", required = true)
    @NotNull(message = "资源状态不能为空")
    private ResourceStatusEnum resourceStatus;
}
