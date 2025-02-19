package cn.bctools.aps.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * @author jvs
 * boolean映射枚举
 */
@Getter
@AllArgsConstructor
public enum BooleanEnum {
    TRUE(Boolean.TRUE, "1"),
    FALSE(Boolean.FALSE, "0"),
    ;

    private final Boolean booleanValue;
    private final String mappingValue;

    public static String getBooleanMappingValue(Boolean booleanValue) {
        for (BooleanEnum value : BooleanEnum.values()) {
            if (value.getBooleanValue().equals(booleanValue)) {
                return value.getMappingValue();
            }
        }
        return null;
    }

}
