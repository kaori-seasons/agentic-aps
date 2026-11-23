package cn.windwheel.aps.demand;

import static cn.windwheel.aps.demand.PlanningInput.*;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashSet;
import java.util.List;

final class PlanningChecks {
    static void require(boolean ok, String message) {
        if (!ok) throw new IllegalArgumentException(message);
    }

    static void key(String value) {
        require(
                value != null
                        && !value.isBlank()
                        && value.length() <= 96
                        && value.equals(value.trim())
                        && value.chars().noneMatch(Character::isISOControl),
                "标识为空、超长或含控制字符");
    }

    static void quantity(BigDecimal value) {
        require(
                value != null
                        && value.signum() >= 0
                        && value.scale() <= 6
                        && value.precision() - value.scale() <= 14,
                "数量须为非负数，最多14位整数与6位小数");
    }

    private static void list(List<?> list, int limit) {
        require(
                list != null
                        && list.size() <= limit
                        && list.stream().noneMatch(java.util.Objects::isNull),
                "明细缺失或超过批量限制");
    }

    static ZoneId validate(PlanningInput s) {
        key(s.location());
        key(s.material());
        key(s.unit());
        key(s.zone());
        ZoneId zone;
        try {
            zone = ZoneId.of(s.zone());
        } catch (java.time.DateTimeException ex) {
            throw new IllegalArgumentException("无效时区");
        }
        require(s.asOf() != null && s.historyStart() != null && s.revision() > 0, "缺少快照版本或时间");
        var week = DemandPlanner.week(s.asOf(), zone);
        require(
                s.historyStart().getDayOfWeek() == DayOfWeek.MONDAY
                        && !s.historyStart().isAfter(week)
                        && !s.historyStart().isBefore(week.minusWeeks(156)),
                "已对账历史起点须为最近三年内的周一");
        require(s.weeks() >= 1 && s.weeks() <= 52, "预测范围须为1至52周");
        quantity(s.opening());
        Policy p = s.policy();
        require(p != null, "缺少库存策略");
        if (p.purchased()) key(p.supplier());
        quantity(p.minimum());
        quantity(p.multiple());
        quantity(p.safety());
        if (p.maximumOnHand() != null) quantity(p.maximumOnHand());
        if (p.purchaseBudget() != null || p.unitCost() != null) {
            quantity(p.unitCost());
            quantity(p.purchaseBudget());
            require(p.unitCost().signum() > 0, "预算约束需要正的单位价格");
        }
        if (p.maximumOrder() != null) quantity(p.maximumOrder());
        require(
                p.multiple().signum() > 0
                        && p.nettingWeeks() >= 0
                        && p.nettingWeeks() <= 8
                        && p.protectionWeeks() >= 1
                        && p.protectionWeeks() <= 13,
                "包装、冲销或保护周期无效");
        list(s.sales(), 20000);
        list(s.supplies(), 5000);
        list(s.requirements(), 10000);
        list(s.windows(), 366);
        var versions = new HashSet<List<?>>();
        for (Sale a : s.sales()) {
            key(a.line());
            quantity(a.ordered());
            quantity(a.shipped());
            require(
                    a.revision() > 0
                            && a.knownAt() != null
                            && a.due() != null
                            && !a.knownAt().isAfter(s.asOf())
                            && a.shipped().compareTo(a.ordered()) <= 0
                            && versions.add(List.of(a.line(), a.revision())),
                    "销售行时间、修订号或累计发运无效");
        }
        var ids = new HashSet<String>();
        for (Supply a : s.supplies()) {
            key(a.line());
            quantity(a.ordered());
            quantity(a.received());
            quantity(a.cancelled());
            require(
                    a.availableAt() != null
                            && ids.add(a.line())
                            && a.received().add(a.cancelled()).compareTo(a.ordered()) <= 0,
                    "来料重复或累计数量无效");
        }
        ids.clear();
        for (Requirement a : s.requirements()) {
            key(a.line());
            quantity(a.quantity());
            require(a.requiredAt() != null && ids.add(a.line()), "组件需求重复或无时间");
        }
        Window prev = null;
        for (Window w : s.windows()) {
            require(
                    w.releaseAt() != null
                            && w.availableAt() != null
                            && !w.releaseAt().isBefore(s.asOf())
                            && !w.availableAt().isBefore(w.releaseAt())
                            && (prev == null
                                    || w.availableAt().isAfter(prev.availableAt())
                                            && w.releaseAt().isAfter(prev.releaseAt())),
                    "采购日历窗口须递增且不能早于截点");
            prev = w;
        }
        if (s.assembly() != null) s.assembly().validate();
        validateSignals(s, week);
        StockTargets.derive(s);
        return zone;
    }

    private static void validateSignals(PlanningInput s, LocalDate firstWeek) {
        var signals = s.signals();
        if (signals == null) return;
        if (signals.lifecycle() != null) {
            var curve = signals.lifecycle();
            require(
                    curve.launchWeek() != null
                            && curve.launchWeek().getDayOfWeek() == DayOfWeek.MONDAY
                            && curve.anchorAgeWeek() >= 0
                            && curve.anchorAgeWeek() <= 260,
                    "生命周期起点或锚点周无效");
            quantity(curve.anchorWeeklyDemand());
            list(curve.points(), 156);
            require(curve.points().size() >= 2, "生命周期曲线至少需要两个节点");
            int previous = -1;
            boolean anchorFound = false;
            for (var point : curve.points()) {
                require(
                        point.ageWeek() >= 0
                                && point.ageWeek() > previous
                                && point.ageWeek() <= 260,
                        "生命周期周龄须递增且不超过260周");
                quantity(point.index());
                require(point.index().signum() > 0, "生命周期指数须为正数");
                if (point.ageWeek() == curve.anchorAgeWeek()) anchorFound = true;
                previous = point.ageWeek();
            }
            require(
                    curve.points().get(0).ageWeek() == 0 && anchorFound,
                    "生命周期曲线须从第0周开始并包含锚点周");
        }
        list(signals.availability() == null ? List.of() : signals.availability(), 156);
        var weeks = new HashSet<LocalDate>();
        for (var row : signals.availability() == null ? List.<AvailabilityObservation>of() : signals.availability()) {
            require(
                    row.week() != null
                            && row.week().getDayOfWeek() == DayOfWeek.MONDAY
                            && !row.week().isBefore(s.historyStart())
                            && row.week().isBefore(firstWeek)
                            && weeks.add(row.week()),
                    "缺货观察周须位于已关闭的历史范围且不能重复");
            quantity(row.observedDemand());
            require(
                    row.inStockFraction() != null
                            && row.inStockFraction().compareTo(BigDecimal.ZERO) > 0
                            && row.inStockFraction().compareTo(BigDecimal.ONE) <= 0,
                    "可售比例须大于0且不超过1");
            if (row.comparableDemand() != null) {
                quantity(row.comparableDemand());
                require(
                        row.comparableDemand().compareTo(row.observedDemand()) >= 0,
                        "可比需求上限不得低于实际销量");
            }
        }
        if (signals.costs() == null) return;
        var costs = signals.costs();
        require(
                "ALL_UNITS".equals(costs.pricingMode()) || "INCREMENTAL".equals(costs.pricingMode()),
                "阶梯计价方式须为ALL_UNITS或INCREMENTAL");
        list(costs.tiers(), 20);
        require(!costs.tiers().isEmpty(), "阶梯报价至少需要一档");
        BigDecimal previous = BigDecimal.ZERO;
        for (var tier : costs.tiers()) {
            quantity(tier.fromQuantity());
            quantity(tier.unitPrice());
            require(
                    tier.fromQuantity().signum() > 0
                            && tier.unitPrice().signum() > 0
                            && tier.fromQuantity().compareTo(previous) > 0,
                    "阶梯起量和单价须为正数且起量递增");
            previous = tier.fromQuantity();
        }
        require(
                costs.tiers().get(0).fromQuantity().compareTo(BigDecimal.ONE) == 0,
                "第一档报价必须从1件开始");
        quantity(costs.freightFixedCost());
        quantity(costs.processingFixedCost());
        quantity(costs.processingUnitCost());
        quantity(costs.holdingCostPerUnitWeek());
        require(
                costs.excessHoldingWeeks() >= 0 && costs.excessHoldingWeeks() <= 104,
                "超额库存持有周数须为0至104");
    }
}
