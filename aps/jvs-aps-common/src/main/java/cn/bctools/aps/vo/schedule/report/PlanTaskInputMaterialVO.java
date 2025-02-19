package cn.bctools.aps.vo.schedule.report;

import cn.bctools.aps.annotation.Comment;
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
@ApiModel("甘特图中的任务详情——输入物料信息")
public class PlanTaskInputMaterialVO {
    @Comment("编码")
    private String code;

    @Comment("名称")
    private String name;

    @ApiModelProperty(value = "物料数量", notes = "任务所需当前物料总数")
    private BigDecimal quantity;
}
