package cn.bctools.aps.solve.util;

import cn.bctools.aps.annotation.DurationFormatValidator;
import cn.bctools.aps.enums.DurationFormatTypeEnum;
import cn.bctools.common.exception.BusinessException;
import cn.bctools.common.utils.ObjectNull;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.util.regex.Matcher;

/**
 * @author jvs
 * 时长工具
 */
public class DurationUtils {

    private DurationUtils() {
    }

    /**
     * 一天=24小时
     */
    private static final BigDecimal HOURS_PER_DAYS = new BigDecimal("24");

    /**
     * 一小时=60分钟
     */
    private static final BigDecimal MINUTES_PER_HOURS = new BigDecimal("60");

    /**
     * 一分钟=60秒
     */
    private static final BigDecimal SECONDS_PER_MINUTES= new BigDecimal("60");


    /**
     * 一秒=1000毫秒
     */
    private static final BigDecimal MILLIS_PER_SECONDS= new BigDecimal("1000");

    /**
     * 时长字符串转Duration
     *
     * @param durationString 时长字符串
     * @return duration
     */
    public static Duration convertDuration(String durationString) {
        if (ObjectNull.isNull(durationString)) {
            return Duration.ZERO;
        }
        Matcher matcher = DurationFormatValidator.LOCAL_PART_PATTERN.matcher(durationString);
        Duration duration = null;
        if (matcher.find()) {
            double value = Double.parseDouble(matcher.group(1));
            BigDecimal valueBigdecimal = BigDecimal.valueOf(value);
            String unit = matcher.group(3);
            duration = switch (unit) {
                case "D", "d" -> convertDay(valueBigdecimal);
                case "H", "h" -> convertHours(valueBigdecimal);
                case "M", "m" -> convertMinutes(valueBigdecimal);
                case "S", "s" -> convertSeconds(valueBigdecimal);
                default -> throw new BusinessException("未知的时间单位");
            };
        }
        return duration;
    }

    /**
     * 天数转Duration
     *
     * @param days 天数
     * @return duration
     */
    public static Duration convertDay(BigDecimal days) {
        // 整数部分转为天数
        Duration duration = Duration.ofDays(days.longValue());
        // 小数部分转为小时
        BigDecimal fractionalPart = days.remainder(BigDecimal.ONE);
        if (BigDecimal.ZERO.compareTo(fractionalPart) < 0) {
            BigDecimal hours = fractionalPart.multiply(HOURS_PER_DAYS);
            if (hours.scale() == 1) {
                hours = hours.setScale(1, RoundingMode.DOWN);
            } else {
                hours = hours.setScale(1, RoundingMode.HALF_UP);
            }
            Duration hoursDuration = convertHours(hours);
            return duration.plus(hoursDuration);
        }
        return duration;
    }

    /**
     * 小时转Duration
     *
     * @param hours 小时
     * @return duration
     */
    public static Duration convertHours(BigDecimal hours) {
        // 整数部分转为小时
        Duration duration = Duration.ofHours(hours.longValue());
        // 小数部分转为分钟
        BigDecimal fractionalPart = hours.remainder(BigDecimal.ONE);
        if (BigDecimal.ZERO.compareTo(fractionalPart) < 0) {
            BigDecimal minutes = fractionalPart.multiply(MINUTES_PER_HOURS);
            if (minutes.scale() == 1) {
                minutes = minutes.setScale(1, RoundingMode.DOWN);
            } else {
                minutes = minutes.setScale(1, RoundingMode.HALF_UP);
            }
            Duration minutesDuration = convertMinutes(minutes);
            return duration.plus(minutesDuration);
        }
        return duration;
    }

    /**
     * 分钟转Duration
     *
     * @param minutes 分钟
     * @return duration
     */
    public static Duration convertMinutes(BigDecimal minutes) {
        // 整数部分转为分钟
        Duration duration = Duration.ofMinutes(minutes.longValue());
        // 小数部分转为秒
        BigDecimal fractionalPart = minutes.remainder(BigDecimal.ONE);
        if (BigDecimal.ZERO.compareTo(fractionalPart) < 0) {
            BigDecimal seconds = fractionalPart.multiply(SECONDS_PER_MINUTES);
            if (seconds.scale() == 1) {
                seconds = seconds.setScale(1, RoundingMode.DOWN);
            } else {
                seconds = seconds.setScale(1, RoundingMode.HALF_UP);
            }
            Duration secondsDuration = convertSeconds(seconds);
            return duration.plus(secondsDuration);
        }
        return duration;
    }

    /**
     * 秒转Duration
     *
     * @param seconds 秒
     * @return duration
     */
    public static Duration convertSeconds(BigDecimal seconds) {
        // 整数部分转为秒
        Duration duration = Duration.ofSeconds(seconds.longValue());
        // 小数部分转为毫秒
        BigDecimal fractionalPart = seconds.remainder(BigDecimal.ONE);
        if (BigDecimal.ZERO.compareTo(fractionalPart) < 0) {
            BigDecimal millis = fractionalPart.multiply(MILLIS_PER_SECONDS);
            return duration.plusMillis(millis.longValue());
        }
        return duration;
    }

    /**
     * 时长格式化
     *
     * @param formatType 格式化类型
     * @param time 时长
     * @return 时长转字符串
     */
    public static String formatDuration(DurationFormatTypeEnum formatType, Duration time) {
        StringBuilder format = new StringBuilder();
        switch (formatType) {
            case DAYS_HOURS_MINUTES_SECONDS:
                if (time.toDays() != 0) {
                    format.append(time.toDaysPart()).append("天");
                }
                if (time.toHours() != 0) {
                    format.append(time.toHoursPart()).append("时");
                }
                break;
            case HOURS_MINUTES_SECONDS:
            default:
                if (time.toHours() != 0) {
                    format.append(time.toHours()).append("时");
                }
                break;
        }
        format
                .append(time.toMinutesPart()).append("分")
                .append(time.toSecondsPart()).append("秒");
        return format.toString();
    }


}
