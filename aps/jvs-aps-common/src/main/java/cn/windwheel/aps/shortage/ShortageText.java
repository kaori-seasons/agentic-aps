package cn.windwheel.aps.shortage;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.temporal.ChronoField;

/**
 * 对外文本口径：数量一律十进制字符串，时间一律带时区偏移的 ISO-8601，禁止浮点。
 */
public final class ShortageText {
    private ShortageText() {}

    public static final DateTimeFormatter LOCAL = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final DateTimeFormatter OFFSET = new DateTimeFormatterBuilder()
            .appendPattern("yyyy-MM-dd'T'HH:mm:ss").appendOffsetId().toFormatter();

    /** 线上数量：固定六位小数的十进制字符串。 */
    public static String decimal(BigDecimal value) {
        return value == null ? null : value.setScale(6, RoundingMode.HALF_UP).toPlainString();
    }

    /** 给人看的数量：去掉尾随零。 */
    public static String quantity(BigDecimal value) {
        if (value == null) {
            return "0";
        }
        BigDecimal plain = value.stripTrailingZeros();
        return plain.scale() < 0 ? plain.setScale(0).toPlainString() : plain.toPlainString();
    }

    public static String local(LocalDateTime time) {
        return time == null ? null : time.truncatedTo(java.time.temporal.ChronoUnit.SECONDS).format(LOCAL);
    }

    /** 带偏移的 ISO-8601，例如 2026-11-27T08:12:33+08:00。 */
    public static String iso(LocalDateTime time, ZoneId zone) {
        return time == null ? null : time.truncatedTo(java.time.temporal.ChronoUnit.SECONDS).atZone(zone).format(OFFSET);
    }

    public static String iso(LocalDate date) {
        return date == null ? null : date.toString();
    }

    public static LocalDateTime local(Instant instant, ZoneId zone) {
        return instant == null ? null : LocalDateTime.ofInstant(instant, zone);
    }

    public static String iso(Duration duration) {
        return duration == null ? null : duration.toString();
    }

    /** 解析库里的 ISO 时长，解析不了按零处理，不让一条坏数据中断整批复评。 */
    public static Duration duration(String text) {
        if (text == null || text.isBlank()) {
            return Duration.ZERO;
        }
        try {
            Duration duration = Duration.parse(text);
            return duration.isNegative() ? Duration.ZERO : duration;
        } catch (java.time.format.DateTimeParseException ignored) {
            return Duration.ZERO;
        }
    }

    public static String trim(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
