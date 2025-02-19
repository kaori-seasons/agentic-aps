package cn.bctools.aps.annotation;

import cn.bctools.common.utils.ObjectNull;

import javax.validation.ConstraintValidator;
import javax.validation.ConstraintValidatorContext;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author jvs
 * 时长格式校验实现
 */
public class DurationFormatValidator implements ConstraintValidator<DurationFormat, CharSequence> {

    /**
     * 格式为：数字 + 日期单位。 如：5D 表示5天; 4H 表示4小时
     * 最多允许一位小数。如1.5D 表示1天半
     */
    public static final Pattern LOCAL_PART_PATTERN = Pattern.compile("(\\d+(\\.\\d)?)([DHMSdhms]$)");

    @Override
    public boolean isValid(CharSequence value, ConstraintValidatorContext constraintValidatorContext) {
        if (ObjectNull.isNull(value)) {
            return true;
        }
        String stringValue = value.toString();
        if (stringValue.length() <= 1) {
            return false;
        }
        Matcher matcher = LOCAL_PART_PATTERN.matcher(stringValue);
        return matcher.matches();
    }

    @Override
    public void initialize(DurationFormat constraintAnnotation) {
        ConstraintValidator.super.initialize(constraintAnnotation);
    }
}
