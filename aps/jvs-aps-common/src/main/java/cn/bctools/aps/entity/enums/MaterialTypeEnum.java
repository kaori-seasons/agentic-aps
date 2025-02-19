package cn.bctools.aps.entity.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * @author jvs
 * 物料类型
 */
@Getter
@AllArgsConstructor
public enum MaterialTypeEnum {
    RAW_MATERIAL("RAW_MATERIAL","原材料"),
    SEMI_FINISHED("SEMI_FINISHED","半成品"),
    FINISHED("FINISHED","成品"),
    ;

    @EnumValue
    @JsonValue
    private final String value;
    private final String desc;

}
