package cn.bctools.aps.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * @author jvs
 * 任务合并方式
 */
@Getter
@AllArgsConstructor
public enum TaskMergeTypeEnum {

    SELECTED_TASKS("选中的任务"),
    ;

    /**
     * 合并方式
     */
    private final String name;
}
