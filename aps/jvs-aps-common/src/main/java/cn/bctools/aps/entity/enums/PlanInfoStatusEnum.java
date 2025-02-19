package cn.bctools.aps.entity.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * @author jvs
 * 计划信息类型
 */
@Getter
@AllArgsConstructor
public enum PlanInfoStatusEnum {
    CONFIRMED("CONFIRMED", "已确认"),
    UNCONFIRMED("UNCONFIRMED", "未确认"),
    ;
    @EnumValue
    @JsonValue
    private final String value;
    private final String desc;
}
