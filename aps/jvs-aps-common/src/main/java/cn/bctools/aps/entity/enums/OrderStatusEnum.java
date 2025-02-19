package cn.bctools.aps.entity.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * @author jvs
 * 生产订单状态
 */
@Getter
@AllArgsConstructor
public enum OrderStatusEnum {
    PENDING("PENDING", "待处理"),
    COMPLETED("COMPLETED", "已完成"),
    CANCELLED("CANCELLED", "已取消"),
    ;

    @EnumValue
    @JsonValue
    private final String value;
    private final String desc;
}
