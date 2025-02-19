package cn.bctools.aps.annotation;

import cn.bctools.common.utils.ObjectNull;

import javax.validation.ConstraintValidator;
import javax.validation.ConstraintValidatorContext;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author jvs
 * 产能格式校验实现
 */
public class ThroughputFormatValidator implements ConstraintValidator<ThroughputFormat, CharSequence> {

    /**
     * 多长时间可以处理一批(与处理的数量无关)
     * </p>
     * 格式为：数字 + 日期单位
     */
    public static final Pattern HOW_LONG_CAN_A_BATCH_BE_PROCESSED = Pattern.compile("(\\d+(\\.\\d)?)([DHMSdhms])$");

    /**
     * 处理一个需要多长时间
     * </p>
     * 格式为：数字 + 日期单位 + P
     */
    public static final Pattern HOW_LONG_DOES_IT_TAKE_TO_PROCESSED_ONE = Pattern.compile("(\\d+(\\.\\d)?)([DHMSdhms])([pP])$");

    /**
     * 单位时间可以处理多少个
     * </p>
     * 格式为：数字 + P + 日期单位
     */
    public static final Pattern HOW_MANY_CAN_BE_PROCESSED_PER_UNIT_TIME = Pattern.compile("(\\d+)[pP]([DHMSdhms])$");

    @Override
    public boolean isValid(CharSequence value, ConstraintValidatorContext constraintValidatorContext) {
        if (ObjectNull.isNull(value)) {
            return true;
        }
        String stringValue = value.toString();
        if (stringValue.length() <= 1) {
            return false;
        }

        Matcher matcher1 = HOW_LONG_DOES_IT_TAKE_TO_PROCESSED_ONE.matcher(stringValue);
        if (matcher1.matches()) {
            return true;
        }

        Matcher matcher2 = HOW_MANY_CAN_BE_PROCESSED_PER_UNIT_TIME.matcher(stringValue);
        if (matcher2.matches()) {
            return true;
        }

        Matcher matcher3 = HOW_LONG_CAN_A_BATCH_BE_PROCESSED.matcher(stringValue);
        return matcher3.matches();
    }
}
