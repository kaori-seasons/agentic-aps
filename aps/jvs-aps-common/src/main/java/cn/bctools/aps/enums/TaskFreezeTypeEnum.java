package cn.bctools.aps.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * @author jvs
 * 排产任务锁定/解锁方式
 */
@Getter
@AllArgsConstructor
public enum TaskFreezeTypeEnum {

    SELECTED_TASKS("选中的任务"),
    STARTED_TASKS("所有已开工的任务"),
    ;

    /**
     * 锁定/解锁方式
     */
    private final String name;

}
