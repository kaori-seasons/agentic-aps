package cn.bctools.aps.entity.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * @author jvs
 * 来料订单状态
 */
@Getter
@AllArgsConstructor
public enum IncomingMaterialOrderStatusEnum {
    PROCESSING("PROCESSING", "在途"),
    CLOSED("CLOSED", "已关闭"),
    ;

    @EnumValue
    @JsonValue
    private final String value;
    private final String desc;
}
