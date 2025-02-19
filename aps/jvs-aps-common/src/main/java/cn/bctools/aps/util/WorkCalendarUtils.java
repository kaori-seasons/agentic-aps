package cn.bctools.aps.util;

import cn.bctools.aps.entity.WorkCalendarPO;
import cn.bctools.aps.enums.BooleanEnum;
import cn.bctools.aps.enums.DayOfWeekEnum;
import cn.bctools.aps.solve.model.WorkCalendar;
import cn.bctools.aps.solve.model.WorkModeTime;
import cn.bctools.common.exception.BusinessException;
import cn.bctools.common.utils.BeanCopyUtil;
import cn.bctools.common.utils.ObjectNull;
import com.baomidou.mybatisplus.core.toolkit.StringPool;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * @author jvs
 * 工作日历工具类
 */
public class WorkCalendarUtils {


    /**
     * 转LocalTime的格式
     */
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");

    private WorkCalendarUtils() {
    }

    /**
     * 工作日设置对象转byte[]
     *
     * @param workDaySetting 工作日设置
     * @return byte[]
     */
    public static byte[] workDaySettingToBytes(Map<DayOfWeekEnum, Boolean> workDaySetting) {
        int dayOfWeekEnumLength = DayOfWeekEnum.values().length;
        if (workDaySetting.size() != dayOfWeekEnumLength) {
            throw new BusinessException("工作日设置错误");
        }
        String workDayString = Arrays.stream(DayOfWeekEnum.values())
                .sorted(Comparator.comparingInt(DayOfWeekEnum::ordinal).reversed())
                .map(dayEnum -> BooleanEnum.getBooleanMappingValue(workDaySetting.get(dayEnum)))
                .filter(ObjectNull::isNotNull)
                .collect(Collectors.joining());
        if (workDayString.length() != dayOfWeekEnumLength) {
            throw new BusinessException("工作日设置错误");
        }
        byte workDayByte = (byte) Integer.parseInt(workDayString, 2);
        return new byte[]{workDayByte};
    }

    /**
     * byte[]转工作日设置对象
     *
     * @param workDayBytes 工作日设置byte[]
     * @return 工作日设置对象
     */
    public static Map<DayOfWeekEnum, Boolean> workDayBytesToSettingMap(byte[] workDayBytes) {
        byte workDayByte = workDayBytes[0];
        return IntStream.range(0, DayOfWeekEnum.values().length)
                .boxed()
                .collect(Collectors.toMap(DayOfWeekEnum::getDayOfWeekEnumByIndex, i -> checkDayEnable(workDayByte, i)));

    }

    /**
     * 将工作日历配置转换为可用于排产计算的数据结构
     *
     * @param workCalendars 日历集合
     * @param workModeMap 工作模式
     * @return 工作日历
     */
    public static List<WorkCalendar> convertScheduleCalendar(List<WorkCalendarPO> workCalendars, Map<String, String> workModeMap) {
        return workCalendars.stream()
                .map(workCalendar -> {
                    // 工作日设置转换
                    Map<DayOfWeekEnum, Boolean> workDaySetting = WorkCalendarUtils.workDayBytesToSettingMap(workCalendar.getWorkDays());
                    List<WorkModeTime> workModeTimes = null;
                    if (workModeMap.containsKey(workCalendar.getWorkModeId())) {
                        String workModeValue = workModeMap.get(workCalendar.getWorkModeId());
                        if (ObjectNull.isNotNull(workModeValue)) {
                            List<String> valueList = Arrays.stream(workModeValue.split(StringPool.SEMICOLON)).toList();
                            workModeTimes = valueList.stream()
                                    .map(value -> {
                                        String[] times = value.split(StringPool.DASH);
                                        String startTimeValue = times[0];
                                        String endTimeValue = times[1];
                                        return new WorkModeTime()
                                                .setStart(LocalTime.parse(startTimeValue, TIME_FORMATTER))
                                                .setEnd(LocalTime.parse(endTimeValue, TIME_FORMATTER));
                                    })
                                    .sorted(Comparator.comparing(WorkModeTime::getStart))
                                    .collect(Collectors.toList());
                        }
                        return BeanCopyUtil.copy(workCalendar, WorkCalendar.class)
                                .setWorkDaySetting(workDaySetting)
                                .setWorkModeTimes(workModeTimes);
                    }
                    return null;
                })
                .filter(ObjectNull::isNotNull)
                .collect(Collectors.toList());
    }

    /**
     * 校验指定位是否启用
     *
     * @param workDayByte 工作日byte
     * @param i 校验位
     * @return true-启用，false-未启用
     */
    private static boolean checkDayEnable(byte workDayByte, int i) {
        return ((workDayByte & 0xff) & (1 << i)) != 0;
    }
}
