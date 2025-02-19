package cn.bctools.aps.entity.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * @author jvs
 * 资源状态
 */
@Getter
@AllArgsConstructor
public enum ResourceStatusEnum {
    NORMAL("NORMAL", "正常"),
    MAINTENANCE("MAINTENANCE", "维修"),
    SCRAPPED("SCRAPPED", "报废"),
    ;

    @EnumValue
    @JsonValue
    private final String value;
    private final String desc;
}
