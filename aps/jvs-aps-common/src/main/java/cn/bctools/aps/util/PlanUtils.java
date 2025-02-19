package cn.bctools.aps.util;

import cn.bctools.aps.dto.schedule.TaskDTO;
import cn.bctools.aps.entity.PlanTaskOrderPO;
import cn.bctools.aps.entity.ProductionResourcePO;
import cn.bctools.aps.entity.dto.ProcessRouteNodePropertiesDTO;
import cn.bctools.aps.entity.dto.ProcessUseMaterialsDTO;
import cn.bctools.aps.entity.dto.ReportFieldDTO;
import cn.bctools.aps.entity.dto.plan.PlanMaterialInfoDTO;
import cn.bctools.aps.entity.dto.plan.PlanOrderInfoDTO;
import cn.bctools.aps.entity.dto.plan.PlanTaskInputMaterialDTO;
import cn.bctools.aps.entity.enums.GanttFieldEnum;
import cn.bctools.aps.entity.enums.PlanTaskStatusEnum;
import cn.bctools.aps.enums.DurationFormatTypeEnum;
import cn.bctools.aps.solve.model.WorkCalendar;
import cn.bctools.aps.solve.util.DurationUtils;
import cn.bctools.aps.solve.util.TaskCalendarUtils;
import cn.bctools.aps.solve.util.TaskDurationUtils;
import cn.bctools.aps.vo.DetailReportFieldSettingVO;
import cn.bctools.aps.vo.schedule.report.ResourceGanttTaskVO;
import cn.bctools.common.exception.BusinessException;
import cn.bctools.common.utils.BeanCopyUtil;
import cn.bctools.common.utils.ObjectNull;
import cn.hutool.core.date.LocalDateTimeUtil;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * @author jvs
 * 计划工具
 */
public class PlanUtils {
    private PlanUtils() {
    }

    private static final Random RANDOM = new Random();

    /**
     * 格式化逾期时长
     *
     * @param task 任务
     * @param now  当前时间
     * @return 逾期时长字符串
     */
    public static String formatOverdueTimeString(TaskDTO task, LocalDateTime now) {
        if (ObjectNull.isNull(task.getTaskStatus())) {
            return null;
        }
        // 任务预计完成时间
        LocalDateTime taskEndTime = task.getEndTime();
        // 用于进行时间比较的时间(默认为当前时间)
        LocalDateTime comparisonTime = now;
        // 若任务已完成，则设置比较时间为实际完成时间
        if (PlanTaskStatusEnum.COMPLETED.equals(task.getTaskStatus())) {
            comparisonTime = task.getLastCompletionTime();
        }

        // 计算逾期时间
        Duration overdueTime = Duration.between(taskEndTime, comparisonTime);
        if (overdueTime.compareTo(Duration.ZERO) > 0) {
            return DurationUtils.formatDuration(DurationFormatTypeEnum.DAYS_HOURS_MINUTES_SECONDS, overdueTime);
        }
        return null;
    }

    /**
     * 格式化延期时长
     *
     * @param taskEndTime  任务完成时间
     * @param deliveryTime 订单交付时间
     * @return 逾期时长字符串
     */
    public static String formatDelayTimeString(LocalDateTime taskEndTime, LocalDateTime deliveryTime) {
        if (ObjectNull.isNull(taskEndTime)) {
            return null;
        }
        // 计算逾期时间
        Duration overdueTime = Duration.between(deliveryTime, taskEndTime);
        if (overdueTime.compareTo(Duration.ZERO) > 0) {
            return DurationUtils.formatDuration(DurationFormatTypeEnum.DAYS_HOURS_MINUTES_SECONDS, overdueTime);
        }
        return null;
    }

    /**
     * 根据字段配置动态获取扩展字段数据
     *
     * @param report           甘特任务信息
     * @param taskDto          任务
     * @param tasks            任务集合
     * @param fieldSetting     任务字段设置
     * @param planTaskOrderMap 任务订单Map
     * @param resourceMap      资源Map
     */
    public static void fetchDynamicFields(ResourceGanttTaskVO report,
                                          TaskDTO taskDto,
                                          List<TaskDTO> tasks,
                                          DetailReportFieldSettingVO fieldSetting,
                                          Map<String, PlanTaskOrderPO> planTaskOrderMap,
                                          Map<String, ProductionResourcePO> resourceMap) {
        if (taskDto.getMergeTask()) {
            report.setExtras(PlanUtils.fetchDynamicFields(taskDto, fieldSetting, planTaskOrderMap, resourceMap));
        } else {
            report.setExtras(PlanUtils.fetchDynamicFields(taskDto, fieldSetting, planTaskOrderMap, resourceMap));
        }
    }

    /**
     * 根据字段配置动态获取扩展字段数据
     *
     * @param task             任务
     * @param fieldSetting     任务字段设置
     * @param planTaskOrderMap 任务订单map
     * @return 数据
     */
    public static Map<String, Object> fetchDynamicFields(TaskDTO task,
                                                          DetailReportFieldSettingVO fieldSetting,
                                                          Map<String, PlanTaskOrderPO> planTaskOrderMap) {
        return fetchDynamicFields(task, fieldSetting, planTaskOrderMap, Collections.emptyMap());
    }

    /**
     * 根据字段配置动态获取扩展字段数据
     *
     * @param task             任务
     * @param fieldSetting     任务字段设置
     * @param planTaskOrderMap 任务订单map
     * @param resourceMap      资源map
     * @return 数据
     */
    public static Map<String, Object> fetchDynamicFields(TaskDTO task,
                                                         DetailReportFieldSettingVO fieldSetting,
                                                         Map<String, PlanTaskOrderPO> planTaskOrderMap,
                                                         Map<String, ProductionResourcePO> resourceMap) {
        // 根据字段key获取字段枚举
        Set<String> fieldKeys = Stream.of(fieldSetting.getTaskBarFields(), fieldSetting.getTooltipFields())
                .flatMap(Collection::stream)
                .map(ReportFieldDTO::getFieldKey)
                .collect(Collectors.toSet());
        List<GanttFieldEnum> ganttFieldEnums = GanttFieldEnum.listNonDefaultFields(fieldSetting.getReportType(), fieldKeys);
        if (ObjectNull.isNull(ganttFieldEnums)) {
            return Collections.emptyMap();
        }
        // 根据字段获取数据
        Map<String, Object> extras = new HashMap<>();
        PlanTaskOrderPO planTaskOrder = Optional.ofNullable(planTaskOrderMap.get(task.getProductionOrderId()))
                .orElseGet(PlanTaskOrderPO::new);
        PlanOrderInfoDTO planOrderInfo = Optional.ofNullable(planTaskOrder.getOrderInfo()).orElseGet(PlanOrderInfoDTO::new);
        PlanMaterialInfoDTO planOrderMaterialInfo = Optional.ofNullable(planTaskOrder.getOrderMaterialInfo()).orElseGet(PlanMaterialInfoDTO::new);
        ProductionResourcePO resource = Optional.ofNullable(resourceMap.get(task.getMainResourceId()))
                .orElseGet(ProductionResourcePO::new);
        ganttFieldEnums.forEach(field -> {
            String fieldKey = field.getFieldKey();
            Object value = switch (field) {
                case ORDER_CODE -> planOrderInfo.getCode();
                case DELIVERY_TIME -> {
                    if (ObjectNull.isNull(planOrderInfo.getDeliveryTime())) {
                        yield null;
                    } else {
                        yield LocalDateTimeUtil.formatNormal(planOrderInfo.getDeliveryTime());
                    }
                }
                case MATERIAL_CODE -> planOrderMaterialInfo.getCode();
                case MATERIAL_NAME -> planOrderMaterialInfo.getName();
                case ORDER_MATERIAL_QUANTITY ->
                        BigDecimalUtils.stripTrailingZeros(planOrderInfo.getQuantity());
                case SCHEDULED_QUANTITY -> BigDecimalUtils.stripTrailingZeros(task.getScheduledQuantity());
                case PROCESS_CODE -> task.getProcessInfo().getCode();
                case PROCESS_NAME -> task.getProcessInfo().getName();
                case CODE -> task.getCode();
                case START_TIME -> task.getStartTime();
                case END_TIME -> task.getEndTime();
                case MAIN_RESOURCE_NAME -> resource.getName();
                case TASK_STATUS -> task.getTaskStatus();
                case OVERDUE_TIME -> PlanUtils.formatOverdueTimeString(task, LocalDateTime.now());
                case DELAY_TIME_STRING -> planOrderInfo.getDeliveryTime();
                default -> null;
            };
            extras.put(fieldKey, value);
        });
        return extras;
    }

    /**
     * 获取任务输入物料
     * <p>
     *
     * @param quantity 生产数量
     * @param process  工序
     * @return 当前任务输入物料集合
     */
    public static List<PlanTaskInputMaterialDTO> getInputMaterial(BigDecimal quantity, ProcessRouteNodePropertiesDTO process) {
        if (ObjectNull.isNull(process.getUseMaterials())) {
            return Collections.emptyList();
        }
        return process.getUseMaterials().stream()
                .filter(ProcessUseMaterialsDTO::getUse)
                .map(material -> {
                    PlanTaskInputMaterialDTO useMaterial = BeanCopyUtil.copy(material, PlanTaskInputMaterialDTO.class);
                    BigDecimal useQuantity = useMaterial.getQuantity().multiply(quantity).setScale(6, RoundingMode.HALF_UP);
                    useMaterial.setQuantity(useQuantity);
                    return useMaterial;
                })
                .collect(Collectors.toList());
    }

    /**
     * 转换任务信息为资源甘特任务图信息
     *
     * @param tasks                    任务集合
     * @param detailReportFieldSetting 任务可视化字段设置
     * @param resources                资源集合
     * @param planTaskOrderList        任务订单集合
     * @return 甘特图中任务信息
     */
    public static List<ResourceGanttTaskVO> convertGanttTaskList(List<TaskDTO> tasks,
                                                                 DetailReportFieldSettingVO detailReportFieldSetting,
                                                                 List<ProductionResourcePO> resources,
                                                                 List<PlanTaskOrderPO> planTaskOrderList,
                                                                 Map<String, List<WorkCalendar>> workCalendarMap) {
        Map<String, PlanTaskOrderPO> planTaskOrderMap = planTaskOrderList.stream()
                .collect(Collectors.toMap(PlanTaskOrderPO::getOrderId, Function.identity()));
        Map<String, ProductionResourcePO> productionResourceMap = resources.stream()
                .collect(Collectors.toMap(ProductionResourcePO::getId, Function.identity()));
        LocalDateTime now = LocalDateTime.now();
        return tasks.stream()
                .filter(task -> ObjectNull.isNull(task.getMergeTaskCode()))
                .map(task -> {
                    ResourceGanttTaskVO report = BeanCopyUtil.copy(task, ResourceGanttTaskVO.class);
                    if (ObjectNull.isNull(task.getCompliant())) {
                        report.setCompliant(true);
                    }
                    report.setOverdueTimeString(PlanUtils.formatOverdueTimeString(task, now));
                    report.setTaskProgressTime(calculateTaskProgressTime(task, workCalendarMap.get(task.getMainResourceId())));

                    // 根据参数设置动态填充字段
                    fetchDynamicFields(report, task, tasks, detailReportFieldSetting, planTaskOrderMap, productionResourceMap);
                    return report;
                })
                .toList();
    }

    /**
     * 计算任务进度时间
     *
     * @param task 任务
     * @param resourceWorkCalendars 任务计划资源使用的日历集合
     * @return 任务进度时间
     */
    private static LocalDateTime calculateTaskProgressTime(TaskDTO task, List<WorkCalendar> resourceWorkCalendars) {
        if (task.getQuantityCompleted().compareTo(BigDecimal.ZERO) == 0) {
            return null;
        }
        Duration taskDuration = TaskDurationUtils.calculateTaskDuration(task.getMainResourceId(), task.getProcessInfo(), task.getQuantityCompleted());
        return TaskCalendarUtils.calculateEndTime(task.getStartTime(), taskDuration, resourceWorkCalendars);
    }


    /**
     * 生成任务编码
     *
     * @param prefix 编码前缀
     * @return 任务编码
     */
    public static String generateTaskCode(String prefix) {
        // 生成当前时间戳
        long timestamp = System.currentTimeMillis();
        // 生成一个随机数
        int randomInt = RANDOM.nextInt(1000000000);
        // 将时间戳和随机数组合为字符串
        String source = timestamp + "-" + randomInt;
        try {
            // 使用SHA-256生成哈希
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(source.getBytes(StandardCharsets.UTF_8));
            // 将哈希值进行Base64编码，并去掉特殊字符
            String base64 = Base64.getUrlEncoder().withoutPadding().encodeToString(hash).replaceAll("[^a-zA-Z0-9]", "");
            // 截取前8个字符作为短ID
            return prefix + base64.substring(0, 8).toUpperCase();
        } catch (NoSuchAlgorithmException e) {
            throw new BusinessException("生成编码失败", e);
        }
    }

    /**
     * 计算任务状态
     *
     * @param scheduledQuantity 计划数量
     * @param quantityCompleted 已完成数量
     * @return 任务状态
     */
    public static PlanTaskStatusEnum calculateTaskStatus(BigDecimal scheduledQuantity, BigDecimal quantityCompleted) {
        if (BigDecimal.ZERO.compareTo(quantityCompleted) == 0) {
            return PlanTaskStatusEnum.PENDING;
        } else if (quantityCompleted.compareTo(scheduledQuantity) >= 0) {
            return PlanTaskStatusEnum.COMPLETED;
        } else {
            return PlanTaskStatusEnum.PARTIALLY_COMPLETED;
        }
    }

}
