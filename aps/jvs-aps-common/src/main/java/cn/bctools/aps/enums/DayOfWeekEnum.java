package cn.bctools.aps.enums;

import cn.bctools.common.exception.BusinessException;
import lombok.Getter;

/**
 * @author jvs
 * 周天枚举
 */
@Getter
public enum DayOfWeekEnum {
    /**
     * 星期日
     */
    SUNDAY,
    /**
     * 星期一
     */
    MONDAY,
    /**
     * 星期二
     */
    TUESDAY,
    /**
     * 星期三
     */
    WEDNESDAY,
    /**
     * 星期四
     */
    THURSDAY,
    /**
     * 星期五
     */
    FRIDAY,
    /**
     * 星期六
     */
    SATURDAY;

    /**
     * 获取下标对应的枚举
     *
     * @param index 下标
     * @return 枚举
     */
    public static DayOfWeekEnum getDayOfWeekEnumByIndex(int index) {
        DayOfWeekEnum[] dayOfWeekEnums = DayOfWeekEnum.values();
        if (index >= dayOfWeekEnums.length || index < 0) {
            throw new BusinessException("参数错误");
        }
        return dayOfWeekEnums[index];
    }

    /**
     * 获取日期对应的枚举
     *
     * @param name 日期名
     * @return 枚举
     */
    public static DayOfWeekEnum getDayOfWeekEnumByName(String name) {
        DayOfWeekEnum[] dayOfWeekEnums = DayOfWeekEnum.values();
        for (DayOfWeekEnum dayOfWeekEnum : dayOfWeekEnums) {
            if (dayOfWeekEnum.name().equals(name)) {
                return dayOfWeekEnum;
            }
        }
        throw new BusinessException("参数错误");
    }
}
