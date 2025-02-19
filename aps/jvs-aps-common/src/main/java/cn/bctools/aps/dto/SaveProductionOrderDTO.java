package cn.bctools.aps.dto;

import cn.bctools.aps.annotation.DecimalPlacesMax;
import cn.bctools.aps.entity.enums.OrderStatusEnum;
import cn.bctools.aps.entity.enums.OrderTypeEnum;
import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.experimental.Accessors;

import javax.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * @author jvs
 */
@Data
@Accessors(chain = true)
@ApiModel("新增生产订单")
public class SaveProductionOrderDTO {
    @ApiModelProperty(value = "订单号", required = true)
    @NotBlank(message = "订单号不能为空")
    private String code;

    @ApiModelProperty(value = "物料编码", required = true)
    @NotBlank(message = "物料编码不能为空")
    private String materialCode;

    @ApiModelProperty(value = "需求数量", required = true)
    @DecimalMin(value = "0", message = "需求数量不能小于0")
    @DecimalPlacesMax(value = 6, message = "需求数量最多6位小数")
    private BigDecimal quantity;

    @ApiModelProperty(value = "需求交付时间", required = true)
    @NotNull(message = "需求交付时间不能为空")
    @JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime deliveryTime;

    @ApiModelProperty(value = "优先级")
    @Max(value = 99999, message = "优先级最多5位数")
    @Min(value = 0, message = "优先级不能为负数")
    private Integer priority;

    @ApiModelProperty(value = "订单类型", required = true)
    @NotNull(message = "订单类型不能为空")
    private OrderTypeEnum type;

    @ApiModelProperty(value = "订单状态")
    private OrderStatusEnum orderStatus;

    @ApiModelProperty(value = "显示颜色")
    private String color;

    @ApiModelProperty(value = "true-参与排产，false-不参与排产")
    private Boolean canSchedule;
}
