package cn.bctools.aps.enums;

import lombok.Getter;

/**
 * @author jvs
 * 时长格式化类型枚举
 */
@Getter
public enum DurationFormatTypeEnum {

    /**
     * x天x时x分x秒
     */
    DAYS_HOURS_MINUTES_SECONDS,

    /**
     * x时x分x秒
     */
    HOURS_MINUTES_SECONDS,
    ;

}
