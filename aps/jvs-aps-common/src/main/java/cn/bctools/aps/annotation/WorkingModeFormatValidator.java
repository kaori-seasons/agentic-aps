package cn.bctools.aps.annotation;

import cn.bctools.common.utils.ObjectNull;
import com.baomidou.mybatisplus.core.toolkit.StringPool;
import lombok.Getter;
import lombok.Setter;

import javax.validation.ConstraintValidator;
import javax.validation.ConstraintValidatorContext;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author jvs
 * 工作模式格式校验实现
 * <p>
 * 多个区间以分号分割
 */
public class WorkingModeFormatValidator implements ConstraintValidator<WorkingModeFormat, CharSequence> {

    /**
     * 单个区间格式如：00:00-24:00
     */
    private static final Pattern MODEL_PATTERN = Pattern.compile("^(?:[01]?\\d|2[0-3]):[0-5]?\\d-(?:[01]?\\d|2[0-4]):[0-5]?\\d$");
    /**
     * 转LocalTime的格式
     */
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");
    /**
     * 小时位为24
     */
    private static final String H_24 = "24";
    /**
     * 小时位为24时，分钟位必须设置的值
     */
    private static final String H_24_M_MUST_VALUE = "00";

    /**
     * 结束时间不能为00:00
     */
    private static final String ERROR_END_TIME = "00:00";


    /**
     * 字段名
     */
    private static final String DEFAULT_NAME = "工作时间";

    @Override
    public boolean isValid(CharSequence charSequence, ConstraintValidatorContext context) {
        if (ObjectNull.isNull(charSequence)) {
            return true;
        }
        String stringValue = charSequence.toString();
        if (stringValue.length() < 11) {
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate(DEFAULT_NAME + "格式错误").addConstraintViolation();
            return false;
        }
        // 校验模式格式
        return validateModeFormat(stringValue, context);
    }

    /**
     * 校验模式格式是否符合要求
     *
     * @param stringValue 模式字符串
     * @return true-通过校验，false-格式错误
     */
    private boolean validateModeFormat(String stringValue, ConstraintValidatorContext context) {
        // 校验时间区间格式
        List<String> valueList = Arrays.stream(stringValue.split(StringPool.SEMICOLON)).toList();
        List<TimeInterval> timeIntervalList = new ArrayList<>();
        for (String value : valueList) {
            Matcher matcher = MODEL_PATTERN.matcher(value);
            if (!matcher.matches()) {
                context.disableDefaultConstraintViolation();
                context.buildConstraintViolationWithTemplate(DEFAULT_NAME + "格式错误").addConstraintViolation();
                return false;
            }
            // 开始时间与结束时间不能相同
            String[] times = value.split(StringPool.DASH);
            String startTimeValue = times[0];
            String endTimeValue = times[1];
            if (startTimeValue.equals(endTimeValue)) {
                context.disableDefaultConstraintViolation();
                context.buildConstraintViolationWithTemplate(DEFAULT_NAME + "开始时间不能等于结束时间").addConstraintViolation();
                return false;
            }

            // 结束时间若小时位为24，则分钟位只能为00
            if (endTimeValue.startsWith(H_24) && !H_24_M_MUST_VALUE.equals(endTimeValue.split(StringPool.COLON)[1])) {
                context.disableDefaultConstraintViolation();
                context.buildConstraintViolationWithTemplate(DEFAULT_NAME + "不能大于24点").addConstraintViolation();
                return false;
            }

            // 结束时间不能为00:00
            if (endTimeValue.equals(ERROR_END_TIME)) {
                context.disableDefaultConstraintViolation();
                context.buildConstraintViolationWithTemplate(DEFAULT_NAME + "结束时间不能为00:00").addConstraintViolation();
                return false;
            }

            // 转换为LocalTime
            timeIntervalList.add(new TimeInterval(startTimeValue, endTimeValue));
        }

        // 允许最多存在一个跨天的时间区间，也就是开始时间 > 结束时间的区间
        long spanDayCount = timeIntervalList.stream().filter(time -> time.getStart().isAfter(time.getEnd())).count();
        if (spanDayCount >= 2) {
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate(DEFAULT_NAME + "最多存在一个跨天的时间区间").addConstraintViolation();
            return false;
        }

        // 校验时间区间不能有交叉
        boolean hasIntersection = validateTimeIntervalHasIntersection(timeIntervalList);
        if (hasIntersection) {
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate(DEFAULT_NAME + "不能有交集").addConstraintViolation();
        }
        return !hasIntersection;
    }

    /**
     * 校验时间区间是否有交叉
     *
     * @param timeIntervalList 时间区间集合
     * @return true-有交叉，false-无交叉
     */
    private boolean validateTimeIntervalHasIntersection(List<TimeInterval> timeIntervalList) {
        int timeIntervalSize = timeIntervalList.size();
        if (timeIntervalSize == 1) {
            return false;
        }
        timeIntervalList.sort(Comparator.comparing(TimeInterval::getStart));
        for (int i = 0; i < timeIntervalSize; i++) {
            TimeInterval current = timeIntervalList.get(i);
            LocalTime currentStart = current.getStart();
            LocalTime currentEnd = current.getEnd();
            boolean isCurrentSpanDay = currentStart.isAfter(currentEnd);
            int nextIndexOf = i + 1;
            if (nextIndexOf >= timeIntervalSize) {
                if (isCurrentSpanDay) {
                    nextIndexOf = 0;
                } else {
                    continue;
                }
            }
            TimeInterval next = timeIntervalList.get(nextIndexOf);
            LocalTime nextStart = next.getStart();
            LocalTime nextEnd = next.getEnd();

            // 是否跨天
            boolean isSpanDay = isCurrentSpanDay || nextStart.isAfter(nextEnd);
            boolean check;
            if (isSpanDay) {
                check = (currentEnd.isBefore(nextStart) || currentEnd.equals(nextStart)) &&
                        (nextEnd.isBefore(currentStart) || nextEnd.equals(currentStart));
            } else {
                check = !currentStart.equals(nextStart) &&
                        currentStart.isBefore(currentEnd) &&
                        nextStart.isBefore(nextEnd) &&
                        (nextStart.isAfter(currentEnd) || nextStart.equals(currentEnd));
            }
            if (!check) {
                return true;
            }
        }
        return false;
    }

    @Getter
    @Setter
    private static class TimeInterval {
        private LocalTime start;
        private LocalTime end;
        public TimeInterval(String startTimeValue, String endTimeValue) {
            this.start = LocalTime.parse(startTimeValue, TIME_FORMATTER);
            this.end = LocalTime.parse(endTimeValue, TIME_FORMATTER);
        }
    }
}
