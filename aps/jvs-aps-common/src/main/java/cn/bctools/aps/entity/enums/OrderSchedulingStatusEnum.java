package cn.bctools.aps.entity.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * @author jvs
 * 生产订单排产状态
 */
@Getter
@AllArgsConstructor
public enum OrderSchedulingStatusEnum {
    UNSCHEDULED("UNSCHEDULED", "未排产"),
    SCHEDULED("SCHEDULED", "已排产"),
    COMPLETED("COMPLETED", "已完成"),
    NO_SCHEDULED("NO_SCHEDULED", "无需排产"),
    ;

    @EnumValue
    @JsonValue
    private final String value;
    private final String desc;
}
