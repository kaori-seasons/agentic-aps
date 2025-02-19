package cn.bctools.aps.entity.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * @author jvs
 * 排程任务状态
 */
@Getter
@AllArgsConstructor
public enum PlanTaskStatusEnum {

    /**
     * 任务尚未报工
     */
    PENDING("PENDING", "待完成"),
    /**
     * 任务已经部分完成，尚未结束
     */
    PARTIALLY_COMPLETED("PARTIALLY_COMPLETED", "部分完成"),
    /**
     * 任务完全结束
     */
    COMPLETED("COMPLETED", "已完成"),
    ;

    @EnumValue
    @JsonValue
    private final String value;
    private final String desc;
}
