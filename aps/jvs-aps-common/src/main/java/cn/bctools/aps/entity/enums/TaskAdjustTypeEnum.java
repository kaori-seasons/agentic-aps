package cn.bctools.aps.entity.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * @author jvs
 * 排产任务调整类型
 */
@Getter
@AllArgsConstructor
public enum TaskAdjustTypeEnum {

    SPLIT("任务拆分"),
    FREEZE("任务锁定"),
    UNFREEZE("任务解锁"),
    ;

    private final String name;

}
