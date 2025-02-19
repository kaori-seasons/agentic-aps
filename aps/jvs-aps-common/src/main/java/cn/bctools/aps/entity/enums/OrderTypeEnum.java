package cn.bctools.aps.entity.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * @author jvs
 * 生产订单类型
 */
@Getter
@AllArgsConstructor
public enum OrderTypeEnum {
    MANUFACTURE("MANUFACTURE", "制造订单"),
    ;

    @EnumValue
    @JsonValue
    private final String value;
    private final String desc;
}
