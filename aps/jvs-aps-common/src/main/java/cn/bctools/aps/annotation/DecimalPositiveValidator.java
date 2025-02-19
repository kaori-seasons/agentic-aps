package cn.bctools.aps.annotation;

import cn.bctools.common.utils.ObjectNull;

import javax.validation.ConstraintValidator;
import javax.validation.ConstraintValidatorContext;
import java.math.BigDecimal;

/**
 * @author jvs
 * BigDecimal必须正数实现
 */
public class DecimalPositiveValidator implements ConstraintValidator<DecimalPositive, BigDecimal> {

    @Override
    public boolean isValid(BigDecimal value, ConstraintValidatorContext constraintValidatorContext) {
        if (ObjectNull.isNull(value)) {
            return true;
        }
        return value.compareTo(BigDecimal.ZERO) > 0;
    }
}
