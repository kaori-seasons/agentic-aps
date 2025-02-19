package cn.bctools.aps.solve.util;

import cn.bctools.aps.annotation.ThroughputFormatValidator;
import cn.bctools.aps.entity.dto.ProcessRouteNodePropertiesDTO;
import cn.bctools.aps.entity.dto.ProcessUseMainResourcesDTO;
import cn.bctools.aps.util.BigDecimalUtils;
import cn.bctools.common.exception.BusinessException;
import cn.bctools.common.utils.ObjectNull;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.util.Optional;
import java.util.regex.Matcher;

/**
 * @author jvs
 * 任务持续时长计算工具类
 */
public class TaskDurationUtils {

    private TaskDurationUtils() {
    }

    /**
     * 一天=86400秒
     */
    private static final BigDecimal SECONDS_PER_DAYS = new BigDecimal("86400");

    /**
     * 一天=1440分
     */
    private static final BigDecimal MINUTES_PER_DAYS = new BigDecimal("1440");


    /**
     * 一天=24时
     */
    private static final BigDecimal HOURS_PER_DAYS = new BigDecimal("24");

    /**
     * 任务持续时长
     *
     * @return 任务持续时长（不包含休息时间）
     */
    public static Duration calculateTaskDuration(String mainResourceId, ProcessRouteNodePropertiesDTO process, BigDecimal quantity) {
        Optional<ProcessUseMainResourcesDTO> processUseMainResources = process.getUseMainResources()
                .stream()
                .filter(res -> res.getId().equals(mainResourceId))
                .findFirst();
        if (processUseMainResources.isPresent()) {
            String throughput = processUseMainResources.get().getThroughput();
            return calculateTaskDuration(throughput, quantity);
        }
        return null;
    }


    /**
     * 计算产能在指定时长内生产数量
     *
     * @param throughput 产能
     * @param duration   时长
     * @return 指定时长内生产数量
     */
    public static BigDecimal calculateTotalOutput(String throughput, Duration duration) {
        // 将产能格式转为每小时产能
        BigDecimal dayThrough = calculateDayThrough(throughput);
        // 将时长转为天数
        BigDecimal days = BigDecimal.valueOf(duration.toSeconds()).divide(SECONDS_PER_DAYS, 2, RoundingMode.HALF_UP);
        return BigDecimalUtils.stripTrailingZeros(dayThrough.multiply(days).setScale(6, RoundingMode.HALF_UP));
    }

    /**
     * 计算任务持续时长
     *
     * @param throughput 产能
     * @param quantity   生产数量
     * @return 任务持续时长（不包含休息时间）
     */
    private static Duration calculateTaskDuration(String throughput, BigDecimal quantity) {
        // 任务持续时长
        Duration taskDuration = null;
        // 产能格式为“处理一个需要多长时间“，计算任务持续时长
        taskDuration = calculateDuration(throughput, quantity);
        if (ObjectNull.isNotNull(taskDuration)) {
            return taskDuration;
        }

        // 产能格式为“单位时间可以处理多少个“，计算任务持续时长
        taskDuration = calculateDurationByThroughput(throughput, quantity);
        if (ObjectNull.isNotNull(taskDuration)) {
            return taskDuration;
        }

        // 产能格式为“多长时间可以处理一批“，计算任务持续时长
        taskDuration = calculateTotalDurationFromBatchTime(throughput);
        if (ObjectNull.isNotNull(taskDuration)) {
            return taskDuration;
        }

        throw new BusinessException("未配置产能或产能格式错误");
    }


    /**
     * 根据处理单个产品所需的时间计算总时长
     *
     * @param throughput 产能
     * @param quantity   数量
     * @return 总时长
     */
    private static Duration calculateDuration(String throughput, BigDecimal quantity) {
        Matcher matcher = ThroughputFormatValidator.HOW_LONG_DOES_IT_TAKE_TO_PROCESSED_ONE.matcher(throughput);
        Duration duration = null;
        if (matcher.find()) {
            double value = Double.parseDouble(matcher.group(1));
            BigDecimal sumThroughput = BigDecimal.valueOf(value).multiply(quantity).setScale(1, RoundingMode.HALF_UP);
            String unit = matcher.group(3);
            duration = calculate(unit, sumThroughput);
        }
        return duration;
    }

    /**
     * 根据单位时间内可处理的产品数量计算总时长
     *
     * @param throughput 产能
     * @param quantity   数量
     * @return 总时长
     */
    private static Duration calculateDurationByThroughput(String throughput, BigDecimal quantity) {
        Matcher matcher = ThroughputFormatValidator.HOW_MANY_CAN_BE_PROCESSED_PER_UNIT_TIME.matcher(throughput);
        Duration duration = null;
        if (matcher.find()) {
            double value = Double.parseDouble(matcher.group(1));
            BigDecimal sumThroughput = quantity.divide(BigDecimal.valueOf(value), 2, RoundingMode.HALF_UP);
            String unit = matcher.group(2);
            duration = calculate(unit, sumThroughput);
        }
        return duration;
    }


    /**
     * 根据处理一批任务所需的时间配置计算总时长。
     *
     * @param throughput 产能
     * @return 总时长
     */
    private static Duration calculateTotalDurationFromBatchTime(String throughput) {
        Matcher matcher = ThroughputFormatValidator.HOW_LONG_CAN_A_BATCH_BE_PROCESSED.matcher(throughput);
        Duration duration = null;
        if (matcher.find()) {
            double value = Double.parseDouble(matcher.group(1));
            BigDecimal sumThroughput = BigDecimal.valueOf(value);
            String unit = matcher.group(3);
            duration = calculate(unit, sumThroughput);
        }
        return duration;
    }

    /**
     * 计算时长
     *
     * @param unit          时间单位
     * @param sumThroughput 时间数字
     * @return 时长
     */
    private static Duration calculate(String unit, BigDecimal sumThroughput) {
        return switch (unit) {
            case "D", "d" -> DurationUtils.convertDay(sumThroughput);
            case "H", "h" -> DurationUtils.convertHours(sumThroughput);
            case "M", "m" -> DurationUtils.convertMinutes(sumThroughput);
            case "S", "s" -> DurationUtils.convertSeconds(sumThroughput);
            default -> throw new BusinessException("未知的时间单位");
        };
    }

    /**
     * 根据产能配置，计算日产能
     *
     * @param throughput 产能
     * @return 日产能
     */
    private static BigDecimal calculateDayThrough(String throughput) {
        // 日产能
        BigDecimal dayThrough = null;

        // 产能格式为“处理一个需要多长时间“，转为日产能
        dayThrough = convertHowLongDoesItTakeToProcessedOne(throughput);
        if (ObjectNull.isNotNull(dayThrough)) {
            return dayThrough;
        }

        // 产能格式为“单位时间可以处理多少个“，转为日产能
        dayThrough = convertHowManyCanBeProcessedPerUnitTime(throughput);
        if (ObjectNull.isNotNull(dayThrough)) {
            return dayThrough;
        }

        throw new BusinessException("产能时间单位转为日产能失败");
    }

    /**
     * 将处理单个产品所需的时间单位转为天
     *
     * @param throughput 产能
     * @return 日产能(一小时做多少个)
     */
    private static BigDecimal convertHowLongDoesItTakeToProcessedOne(String throughput) {
        Matcher matcher = ThroughputFormatValidator.HOW_LONG_DOES_IT_TAKE_TO_PROCESSED_ONE.matcher(throughput);
        BigDecimal hourThroughput = null;
        if (matcher.find()) {
            String value = String.valueOf(Double.parseDouble(matcher.group(1)));
            String unit = matcher.group(3);
            BigDecimal hourValue = switch (unit) {
                case "D", "d" -> BigDecimal.ONE.divide(new BigDecimal(value), 1, RoundingMode.HALF_UP);
                case "H", "h" -> HOURS_PER_DAYS.divide(new BigDecimal(value), 1, RoundingMode.HALF_UP);
                case "M", "m" -> MINUTES_PER_DAYS.divide(new BigDecimal(value), 1, RoundingMode.HALF_UP);
                case "S", "s" -> SECONDS_PER_DAYS.divide(new BigDecimal(value), 1, RoundingMode.HALF_UP);
                default -> throw new BusinessException("未知的时间单位");
            };
            hourThroughput = BigDecimalUtils.stripTrailingZeros(hourValue);
        }
        return hourThroughput;
    }

    /**
     * 将单位时间内可处理的产品数量产能格式时间单位转为天
     *
     * @param throughput 产能
     * @return 日产能(一小时做多少个)
     */
    private static BigDecimal convertHowManyCanBeProcessedPerUnitTime(String throughput) {
        Matcher matcher = ThroughputFormatValidator.HOW_MANY_CAN_BE_PROCESSED_PER_UNIT_TIME.matcher(throughput);
        BigDecimal hourThroughput = null;
        if (matcher.find()) {
            String value = String.valueOf(Double.parseDouble(matcher.group(1)));
            String unit = matcher.group(2);
            BigDecimal hourValue = switch (unit) {
                case "D", "d" -> new BigDecimal(value);
                case "H", "h" -> HOURS_PER_DAYS.multiply(new BigDecimal(value)).setScale( 1, RoundingMode.HALF_UP);
                case "M", "m" -> MINUTES_PER_DAYS.multiply(new BigDecimal(value)).setScale( 1, RoundingMode.HALF_UP);
                case "S", "s" -> SECONDS_PER_DAYS.multiply(new BigDecimal(value)).setScale(1, RoundingMode.HALF_UP);
                default -> throw new BusinessException("未知的时间单位");
            };
            hourThroughput = BigDecimalUtils.stripTrailingZeros(hourValue);
        }
        return hourThroughput;
    }
}
