package cn.bctools.aps.entity.dto.plan;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;

/**
 * @author jvs
 */
@Data
@Accessors(chain = true)
@ApiModel("排程任务输入物料信息")
public class PlanTaskInputMaterialDTO {
    @ApiModelProperty(value = "物料id")
    private String id;

    @ApiModelProperty(value = "物料数量", notes = "任务所需当前物料总数")
    private BigDecimal quantity;
}
