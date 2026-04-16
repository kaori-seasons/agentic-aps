package cn.windwheel.aps.entity.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * @author jvs
 * 排产方向
 */
@Getter
@AllArgsConstructor
public enum PlanningDirectionEnum {

    FORWARD("FORWARD", "正排"),
    BACKWARD("BACKWARD", "逆排"),
    ;
    @EnumValue
    @JsonValue
    private final String value;
    private final String desc;
}
