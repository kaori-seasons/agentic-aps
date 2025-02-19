package cn.bctools.aps.dto;

import cn.bctools.aps.annotation.DecimalPlacesMax;
import cn.bctools.aps.annotation.DecimalPositive;
import cn.bctools.aps.entity.enums.IncomingMaterialOrderStatusEnum;
import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.experimental.Accessors;
import org.hibernate.validator.constraints.Length;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * @author jvs
 */
@Data
@Accessors(chain = true)
@ApiModel("新增来料订单")
public class SaveIncomingMaterialOrderDTO {
    @ApiModelProperty(value = "订单号", required = true)
    @NotBlank(message = "订单号不能为空")
    @Length(max = 30, message = "订单号最多30个字符")
    private String code;

    @ApiModelProperty(value = "物料编码", required = true)
    @NotBlank(message = "物料编码不能为空")
    private String materialCode;

    @ApiModelProperty(value = "数量", required = true)
    @NotNull(message = "数量不能为空")
    @DecimalPositive(message = "物料数量必须是正数")
    @DecimalPlacesMax(value = 6, message = "物料数量最多6位小数")
    private BigDecimal quantity;

    @ApiModelProperty(value = "预计到货时间", required = true)
    @NotNull(message = "预计到货时间不能为空")
    @JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime deliveryTime;

    @ApiModelProperty(value = "状态", required = true)
    @NotNull(message = "订单状态不能为空")
    private IncomingMaterialOrderStatusEnum orderStatus;
}
