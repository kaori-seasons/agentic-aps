package cn.bctools.aps.entity.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * @author jvs
 * 报表/甘特图枚举
 */
@Getter
@AllArgsConstructor
public enum ReportTypeEnum {

    /**
     * 以列表形式展示排程任务
     */
    PLAN_TASK_LIST( "计划工单"),

    /**
     * 以甘特图形式展示分配给任务的主资源和时间安排
     */
    PLAN_RESOURCE_TASK_GANTT(  "资源甘特图"),

    /**
     * 以甘特图形式展示订单任务的时间安排
     */
    PLAN_ORDER_TASK_GANTT( "订单甘特图"),

    /**
     * 以甘特图形式展示每日所需物料
     */
    PLAN_MATERIAL_REQUIREMENT_GANTT( "物料需求甘特图"),
    ;

    private final String reportName;

}
