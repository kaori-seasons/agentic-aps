package cn.bctools.aps.entity.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * @author jvs
 * 物料来源
 */
@Getter
@AllArgsConstructor
public enum MaterialSourceEnum {
    PRODUCED("PRODUCED", "制造"),
    PURCHASED("PURCHASED", "采购"),
    ;

    @EnumValue
    @JsonValue
    private final String value;
    private final String desc;
}
