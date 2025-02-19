package cn.bctools.aps.util;

import cn.bctools.common.utils.ObjectNull;

import java.math.BigDecimal;

/**
 * @author jvs
 */
public class BigDecimalUtils {
    private BigDecimalUtils() {
    }

    /**
     * 去除尾部多余的零，保留所有有效位数，且避免返回科学计数法
     *
     * @param value 原值
     * @return 转换后的值
     */
    public static BigDecimal stripTrailingZeros(BigDecimal value) {
        if (ObjectNull.isNull(value)) {
            return null;
        }
        String stripTrailingZerosString = value.stripTrailingZeros().toPlainString();
        return new BigDecimal(stripTrailingZerosString);
    }
}
