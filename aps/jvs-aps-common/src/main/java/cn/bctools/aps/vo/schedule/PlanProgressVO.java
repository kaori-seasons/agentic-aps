package cn.bctools.aps.vo.schedule;

import cn.bctools.aps.solve.enums.SolveProgressStatusEnum;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * @author jvs
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@ApiModel("排产进度")
public class PlanProgressVO {
    @ApiModelProperty("排程进度状态")
    private SolveProgressStatusEnum status;

    @ApiModelProperty("进度百分比")
    private Long ratio;

    @ApiModelProperty("进度内容")
    List<Object> contents;
}
