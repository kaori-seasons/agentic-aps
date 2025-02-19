package cn.bctools.aps.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * @author jvs
 * 移动任务方式
 */
@Getter
@AllArgsConstructor
public enum TaskMoveTypeEnum {

    MOVE_POSITION("移动单个任务"),
    MOVE_PARTIALLY_COMPLETED_TASK_TIME("移动部分完成的任务进度到指定时间"),
    ;

    /**
     * 移动任务方式
     */
    private final String name;
}
