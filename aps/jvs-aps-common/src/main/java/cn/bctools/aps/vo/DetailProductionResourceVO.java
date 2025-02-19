package cn.bctools.aps.vo;

import cn.bctools.aps.entity.enums.ResourceStatusEnum;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.math.BigDecimal;

/**
 * @author jvs
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@ApiModel("生产资源详情")
public class DetailProductionResourceVO extends BaseVO {
    @ApiModelProperty(value = "名称")
    private String name;

    @ApiModelProperty(value = "编码")
    private String code;

    @ApiModelProperty(value = "资源组")
    private String resourceGroup;

    @ApiModelProperty(value = "容量")
    private BigDecimal capacity;

    @ApiModelProperty(value = "容量计量单位")
    private String unit;

    @ApiModelProperty(value = "产能。 格式如：15D,10DP,20PD")
    private String throughput;

    @ApiModelProperty(value = "资源状态")
    private ResourceStatusEnum resourceStatus;
}
