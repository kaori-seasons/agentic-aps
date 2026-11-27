package cn.windwheel.aps.shortage;

import cn.windwheel.aps.shortage.ShortageModel.Channel;
import cn.windwheel.aps.shortage.ShortageModel.Severity;
import cn.windwheel.aps.shortage.ShortageRule.Effective;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 规则覆盖。默认值内置在 {@link ShortageRule}，表里只存租户改过的字段，空列表示沿用默认值。
 */
@Repository
@RequiredArgsConstructor
public class ShortageRuleStore {
    static final List<String> UNITS = List.of("DAYS", "HOURS", "MINUTES");

    private final JdbcTemplate db;

    /** 一次覆盖提交。未出现的字段为 null，表示不改。 */
    public record Change(Severity severity, Boolean enabled, Integer thresholdValue, String thresholdUnit,
                         Integer cooldownMinutes, String receivers, String channels, String actionHint) {}

    /** 表里的一行覆盖。 */
    public record Override(ShortageRule rule, Severity severity, Boolean enabled, Integer thresholdValue,
                           String thresholdUnit, Integer cooldownMinutes, String receivers, String channels,
                           String actionHint, String updatedBy, LocalDateTime updatedAt) {}

    public Map<ShortageRule, Override> overrides(String tenant) {
        Map<ShortageRule, Override> result = new EnumMap<>(ShortageRule.class);
        db.query("select rule_code,severity,enabled,threshold_value,threshold_unit,cooldown_minutes,"
                        + "receivers,channels,action_hint,updated_by,updated_at from jvs_shortage_rule where tenant_id=?",
                r -> {
                    ShortageRule rule = ShortageRule.parse(r.getString(1));
                    if (rule == null) {
                        return;
                    }
                    result.put(rule, new Override(rule,
                            r.getString(2) == null ? null : Severity.valueOf(r.getString(2)),
                            r.getObject(3) == null ? null : r.getBoolean(3),
                            r.getObject(4) == null ? null : r.getInt(4),
                            r.getString(5),
                            r.getObject(6) == null ? null : r.getInt(6),
                            r.getString(7), r.getString(8), r.getString(9),
                            r.getString(10), r.getObject(11, LocalDateTime.class)));
                }, tenant);
        return result;
    }

    /** 生效规则：默认值叠加租户覆盖。 */
    public List<Effective> effective(String tenant) {
        Map<ShortageRule, Override> overrides = overrides(tenant);
        List<Effective> result = new ArrayList<>();
        for (ShortageRule rule : ShortageRule.values()) {
            result.add(merge(rule, overrides.get(rule)));
        }
        return result;
    }

    /**
     * 站内台账是唯一必选通道，任何覆盖都不能把它去掉，所以这里总是把 LEDGER 并回去。
     */
    public static Effective merge(ShortageRule rule, Override override) {
        Severity severity = override != null && override.severity() != null ? override.severity() : rule.getSeverity();
        boolean enabled = override != null && override.enabled() != null ? override.enabled() : rule.isEnabled();
        Integer value = override != null && override.thresholdValue() != null ? override.thresholdValue()
                : rule.getThresholdValue();
        String unit = override != null && override.thresholdUnit() != null ? override.thresholdUnit()
                : rule.getThresholdUnit();
        int cooldown = override != null && override.cooldownMinutes() != null
                ? override.cooldownMinutes() : ShortageRule.DEFAULT_COOLDOWN_MINUTES;
        boolean channelsOverridden = override != null && override.channels() != null && !override.channels().isBlank();
        LinkedHashSet<String> channels = new LinkedHashSet<>(List.of(Channel.LEDGER.name()));
        channels.addAll(channelsOverridden ? ShortageRule.split(override.channels()) : rule.channelList());
        boolean receiversOverridden = override != null && override.receivers() != null && !override.receivers().isBlank();
        List<String> receivers = receiversOverridden ? ShortageRule.split(override.receivers()) : rule.receiverList();
        String hint = override != null && override.actionHint() != null && !override.actionHint().isBlank()
                ? override.actionHint() : rule.getActionHint();
        Long minutes = null;
        Long days = null;
        if (value != null) {
            if ("DAYS".equals(unit)) {
                days = value.longValue();
                minutes = days * 1440;
            } else if ("HOURS".equals(unit)) {
                minutes = value.longValue() * 60;
            } else {
                minutes = value.longValue();
            }
        }
        return new Effective(rule, severity, enabled, minutes, days, cooldown, List.copyOf(receivers),
                List.copyOf(channels), hint);
    }

    public Effective save(String tenant, ShortageRule rule, Change change, String by, LocalDateTime now) {
        validate(change);
        // 只改单位不改数值时沿用默认数值；规则本身没有默认阈值的从 0 起。
        boolean thresholdTouched = change.thresholdValue() != null || change.thresholdUnit() != null;
        Integer value = change.thresholdValue() != null ? change.thresholdValue()
                : thresholdTouched && rule.getThresholdValue() != null ? rule.getThresholdValue()
                : thresholdTouched ? 0 : null;
        String unit = change.thresholdUnit() != null ? change.thresholdUnit()
                : thresholdTouched ? rule.getThresholdUnit() == null ? "DAYS" : rule.getThresholdUnit() : null;
        db.update("insert into jvs_shortage_rule(tenant_id,rule_code,dimension,severity,enabled,threshold_value,"
                        + "threshold_unit,cooldown_minutes,receivers,channels,action_hint,updated_by,updated_at)"
                        + " values(?,?,?,?,?,?,?,?,?,?,?,?,?) on duplicate key update"
                        + " severity=coalesce(values(severity),severity),enabled=coalesce(values(enabled),enabled),"
                        + " threshold_value=coalesce(values(threshold_value),threshold_value),"
                        + " threshold_unit=coalesce(values(threshold_unit),threshold_unit),"
                        + " cooldown_minutes=coalesce(values(cooldown_minutes),cooldown_minutes),"
                        + " receivers=coalesce(values(receivers),receivers),channels=coalesce(values(channels),channels),"
                        + " action_hint=coalesce(values(action_hint),action_hint),updated_by=?,updated_at=?",
                tenant, rule.name(), rule.getDimension().name(),
                change.severity() == null ? null : change.severity().name(), change.enabled(), value, unit,
                change.cooldownMinutes(), blankToNull(change.receivers()), blankToNull(change.channels()),
                blankToNull(change.actionHint()), by, now, by, now);
        return merge(rule, new Override(rule, change.severity(), change.enabled(), value, unit,
                change.cooldownMinutes(), blankToNull(change.receivers()), blankToNull(change.channels()),
                blankToNull(change.actionHint()), by, now));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static void validate(Change change) {
        if (change.thresholdUnit() != null && !UNITS.contains(change.thresholdUnit())) {
            throw new ShortageException(422, "阈值单位只支持 DAYS、HOURS、MINUTES");
        }
        if (change.thresholdValue() != null && change.thresholdValue() < 0) {
            throw new ShortageException(422, "阈值不能为负");
        }
        if (change.cooldownMinutes() != null && (change.cooldownMinutes() < 0 || change.cooldownMinutes() > 10080)) {
            throw new ShortageException(422, "冷却期须在 0 到 10080 分钟之间");
        }
        if (change.channels() != null) {
            for (String channel : ShortageRule.split(change.channels())) {
                try {
                    Channel.valueOf(channel);
                } catch (IllegalArgumentException e) {
                    throw new ShortageException(422, "未知通道：" + channel);
                }
            }
        }
        if (change.receivers() != null && change.receivers().length() > 500) {
            throw new ShortageException(422, "接收角色过长");
        }
        if (change.actionHint() != null && change.actionHint().length() > 200) {
            throw new ShortageException(422, "建议动作不能超过 200 字");
        }
    }

    public Optional<Change> current(String tenant, ShortageRule rule) {
        Override override = overrides(tenant).get(rule);
        if (override == null) {
            return Optional.empty();
        }
        return Optional.of(new Change(override.severity(), override.enabled(), override.thresholdValue(),
                override.thresholdUnit(), override.cooldownMinutes(), override.receivers(), override.channels(),
                override.actionHint()));
    }
}
