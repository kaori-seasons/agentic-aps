package cn.bctools.aps.dto.schedule;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.experimental.Accessors;

import javax.validation.constraints.NotNull;

/**
 * @author jvs
 * 统一排产任务锁定/解锁请求基类，所有排产任务锁定/解锁请求类均需继承此类
 */
@Data
@Accessors(chain = true)
@ApiModel("锁定|解锁排产任务")
public class AdjustTaskFreezeDTO {
    @ApiModelProperty(value = "true-锁定，false-解锁", required = true)
    @NotNull(message = "未指定锁定状态")
    private Boolean pinned;
}
