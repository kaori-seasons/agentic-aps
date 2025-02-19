package cn.bctools.aps.dto;

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
@ApiModel("分页查询计划策略")
public class PagePlanningStrategyDTO extends PageBaseDTO {

    @ApiModelProperty(value = "策略名称")
    private String name;

    @ApiModelProperty(value = "false-无效，true-有效")
    private Boolean active;
}
