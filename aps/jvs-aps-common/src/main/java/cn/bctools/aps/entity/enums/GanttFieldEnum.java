package cn.bctools.aps.entity.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * @author jvs
 * 甘特图回显字段
 */
@Getter
@AllArgsConstructor
public enum GanttFieldEnum {

    ORDER_CODE(
            "订单号",
            "orderCode",
            EnumSet.of(ReportTypeEnum.PLAN_RESOURCE_TASK_GANTT, ReportTypeEnum.PLAN_ORDER_TASK_GANTT),
            EnumSet.noneOf(ReportTypeEnum.class)),

    DELIVERY_TIME(
            "需求交付时间",
            "deliveryTime",
            EnumSet.of(ReportTypeEnum.PLAN_RESOURCE_TASK_GANTT, ReportTypeEnum.PLAN_ORDER_TASK_GANTT),
            EnumSet.noneOf(ReportTypeEnum.class)),

    MATERIAL_CODE(
            "主产物编码",
            "materialCode",
            EnumSet.of(ReportTypeEnum.PLAN_RESOURCE_TASK_GANTT, ReportTypeEnum.PLAN_ORDER_TASK_GANTT),
            EnumSet.noneOf(ReportTypeEnum.class)),

    MATERIAL_NAME(
            "主产物",
            "materialName",
            EnumSet.of(ReportTypeEnum.PLAN_RESOURCE_TASK_GANTT, ReportTypeEnum.PLAN_ORDER_TASK_GANTT),
            EnumSet.noneOf(ReportTypeEnum.class)),

    ORDER_MATERIAL_QUANTITY(
            "主产物需求数量",
            "orderMaterialQuantity",
            EnumSet.of(ReportTypeEnum.PLAN_RESOURCE_TASK_GANTT, ReportTypeEnum.PLAN_ORDER_TASK_GANTT),
            EnumSet.noneOf(ReportTypeEnum.class)),

    SCHEDULED_QUANTITY(
            "计划制造数量",
            "scheduledQuantity",
            EnumSet.of(ReportTypeEnum.PLAN_RESOURCE_TASK_GANTT, ReportTypeEnum.PLAN_ORDER_TASK_GANTT),
            EnumSet.noneOf(ReportTypeEnum.class)),

    PROCESS_CODE(
            "工序编码",
            "processCode",
            EnumSet.of(ReportTypeEnum.PLAN_RESOURCE_TASK_GANTT, ReportTypeEnum.PLAN_ORDER_TASK_GANTT),
            EnumSet.of(ReportTypeEnum.PLAN_ORDER_TASK_GANTT)),

    PROCESS_NAME(
            "工序名",
            "processName",
            EnumSet.of(ReportTypeEnum.PLAN_RESOURCE_TASK_GANTT, ReportTypeEnum.PLAN_ORDER_TASK_GANTT),
            EnumSet.noneOf(ReportTypeEnum.class)),

    CODE(
            "任务编码",
            "code",
            EnumSet.of(ReportTypeEnum.PLAN_RESOURCE_TASK_GANTT),
            EnumSet.of(ReportTypeEnum.PLAN_RESOURCE_TASK_GANTT)),

    START_TIME(
            "计划开始时间",
            "startTime",
            EnumSet.of(ReportTypeEnum.PLAN_RESOURCE_TASK_GANTT, ReportTypeEnum.PLAN_ORDER_TASK_GANTT),
            EnumSet.of(ReportTypeEnum.PLAN_RESOURCE_TASK_GANTT, ReportTypeEnum.PLAN_ORDER_TASK_GANTT)),

    END_TIME(
            "计划完成时间",
            "endTime",
            EnumSet.of(ReportTypeEnum.PLAN_RESOURCE_TASK_GANTT, ReportTypeEnum.PLAN_ORDER_TASK_GANTT),
            EnumSet.of(ReportTypeEnum.PLAN_RESOURCE_TASK_GANTT, ReportTypeEnum.PLAN_ORDER_TASK_GANTT)),

    MAIN_RESOURCE_NAME(
            "计划主资源",
            "mainResourceName",
            EnumSet.of(ReportTypeEnum.PLAN_RESOURCE_TASK_GANTT),
            EnumSet.noneOf(ReportTypeEnum.class)),

    TASK_STATUS(
            "任务状态",
            "taskStatus",
            EnumSet.of(ReportTypeEnum.PLAN_RESOURCE_TASK_GANTT, ReportTypeEnum.PLAN_ORDER_TASK_GANTT),
            EnumSet.of(ReportTypeEnum.PLAN_RESOURCE_TASK_GANTT, ReportTypeEnum.PLAN_ORDER_TASK_GANTT)),

    OVERDUE_TIME(
            "逾期时长",
            "overdueTimeString",
            EnumSet.of(ReportTypeEnum.PLAN_RESOURCE_TASK_GANTT, ReportTypeEnum.PLAN_ORDER_TASK_GANTT),
            EnumSet.of(ReportTypeEnum.PLAN_RESOURCE_TASK_GANTT, ReportTypeEnum.PLAN_ORDER_TASK_GANTT)),

    DELAY_TIME_STRING(
            "订单延期时长",
            "delayTimeString",
            EnumSet.of(ReportTypeEnum.PLAN_ORDER_TASK_GANTT),
            EnumSet.of(ReportTypeEnum.PLAN_ORDER_TASK_GANTT)),
    ;


    /**
     * 字段名
     */
    private final String fieldName;

    /**
     * 字段key
     */
    @EnumValue
    @JsonValue
    private final String fieldKey;

    /**
     * 该字段在那些报告下可选
     */
    private final EnumSet<ReportTypeEnum> fieldOptionsByReportTypes;

    /**
     * 该字段在那些报告下会默认返回数据
     */
    private final EnumSet<ReportTypeEnum> fieldDefaultByReportTypes;


    /**
     * 根据字段key获取枚举
     *
     * @param fieldKey 字段key
     * @return 字段枚举
     */
    public static GanttFieldEnum getEnumByFieldKey(String fieldKey) {
        for (GanttFieldEnum value : GanttFieldEnum.values()) {
            if (value.getFieldKey().equals(fieldKey)) {
                return value;
            }
        }
        return null;
    }

    /**
     * 获取指定报告类型可选字段集合
     *
     * @param reportType 报告类型
     * @return 可选字段集合
     */
    public static List<GanttFieldEnum> listFieldOptionsByReportType(ReportTypeEnum reportType) {
        return Arrays.stream(GanttFieldEnum.values())
                .filter(fieldEnum -> fieldEnum.getFieldOptionsByReportTypes().contains(reportType))
                .toList();
    }

    /**
     * 筛选指定报告下不会默认返回数据的字段
     *
     * @param reportType 报告
     * @param fieldKeys  字段key集合
     * @return 不会默认返回数据的字段集合
     */
    public static List<GanttFieldEnum> listNonDefaultFields(ReportTypeEnum reportType, Set<String> fieldKeys) {
        return Arrays.stream(GanttFieldEnum.values())
                .filter(fieldEnum -> fieldKeys.contains(fieldEnum.getFieldKey()))
                .filter(fieldEnum -> !fieldEnum.getFieldDefaultByReportTypes().contains(reportType))
                .toList();
    }

}
