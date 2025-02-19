package cn.bctools.aps.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * @author jvs
 * 排产任务拆分方式
 */
@Getter
@AllArgsConstructor
public enum TaskSplitTypeEnum {

    SPLIT_BY_QUANTITY("拆分数量"),
    SPLIT_EVENLY_BY_TASK_NUMBER("按任务数量平均拆分"),
    SPLIT_BY_COMPLETION("按完成数量拆分");
    private final String name;


}
