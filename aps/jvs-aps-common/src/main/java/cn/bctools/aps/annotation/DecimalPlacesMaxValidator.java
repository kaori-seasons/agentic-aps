package cn.bctools.aps.annotation;

import cn.bctools.common.utils.ObjectNull;

import javax.validation.ConstraintValidator;
import javax.validation.ConstraintValidatorContext;
import java.math.BigDecimal;

/**
 * @author jvs
 * BigDecimal最大小数位数校验
 */
public class DecimalPlacesMaxValidator implements ConstraintValidator<DecimalPlacesMax, BigDecimal> {

    private int maxDecimalPlaces;

    @Override
    public void initialize(DecimalPlacesMax constraintAnnotation) {
        maxDecimalPlaces = constraintAnnotation.value();
    }

    @Override
    public boolean isValid(BigDecimal value, ConstraintValidatorContext constraintValidatorContext) {
        if (ObjectNull.isNull(value)) {
            return true;
        }

        int decimalPlaces = Math.max(value.scale(), 0);
        return decimalPlaces <= maxDecimalPlaces;
    }
}
