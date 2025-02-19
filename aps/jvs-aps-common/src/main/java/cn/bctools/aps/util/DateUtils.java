package cn.bctools.aps.util;

import java.time.LocalDateTime;

/**
 * @author jvs
 * 日期工具类
 */
public class DateUtils {

    private DateUtils() {
    }

    /**
     * 校验时间区间是否有交集
     * <p>
     * 开始时间等于另一个区间的结束时间算作有交集
     *
     * @param start1 区间1-开始
     * @param end1   区间1-结束
     * @param start2 区间2-开始
     * @param end2   区间2-结束
     * @return true-有交集，false-无交集
     */
    public static boolean hasOverlap(LocalDateTime start1, LocalDateTime end1,
                                     LocalDateTime start2, LocalDateTime end2) {
        return hasOverlap(true, start1, end1, start2, end2);
    }

    /**
     * 校验时间区间是否有交集
     *
     * @param considerEdgeEquality 开始时间等于另一个区间的结束时间是否算交集 true-算，false-不算
     * @param start1               区间1-开始
     * @param end1                 区间1-结束
     * @param start2               区间2-开始
     * @param end2                 区间2-结束
     * @return true-有交集，false-无交集
     */
    public static boolean hasOverlap(boolean considerEdgeEquality,
                                     LocalDateTime start1, LocalDateTime end1,
                                     LocalDateTime start2, LocalDateTime end2) {
        if (considerEdgeEquality) {
            return !(start1.isAfter(end2) || end1.isBefore(start2));
        } else {
            return !(start1.isAfter(end2) || end1.isBefore(start2) || end1.isEqual(start2) || end2.isEqual(start1));
        }
    }
}
