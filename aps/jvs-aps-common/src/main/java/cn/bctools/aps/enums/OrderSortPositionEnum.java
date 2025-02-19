package cn.bctools.aps.enums;

import lombok.Getter;

/**
 * @author jvs
 * 排序位置
 */
@Getter
public enum OrderSortPositionEnum {
    /**
     * 移动到目标订单之前
     */
    BEFORE,
    /**
     * 移动到目标订单之后
     */
    AFTER,
    ;
}
