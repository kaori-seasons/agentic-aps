package cn.windwheel.aps.demand;

import static cn.windwheel.aps.demand.PlanningInput.*;

import static java.math.BigDecimal.ZERO;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.*;

/** Pure calculation over a single immutable, reconciled series; never mutates MRP inventory. */
public final class DemandPlanner {
    public record Adjustment(BigDecimal value, String reason, Instant expiresAt) {}

    public record Allocation(
            String line,
            long revision,
            LocalDate originalWeek,
            LocalDate week,
            BigDecimal quantity) {}

    public record Bucket(
            LocalDate week,
            BigDecimal baseline,
            BigDecimal total,
            BigDecimal consumed,
            BigDecimal net) {}

    public record Flow(String kind, String line, BigDecimal change) {}

    public record Point(Instant at, List<Flow> flows, BigDecimal confirmed, BigDecimal scenario) {}

    public record Proposal(
            Instant releaseAt,
            Instant availableAt,
            Instant shortageAt,
            BigDecimal net,
            BigDecimal quantity,
            CostBreakdown cost) {
        public Proposal(
                Instant releaseAt,
                Instant availableAt,
                Instant shortageAt,
                BigDecimal net,
                BigDecimal quantity) {
            this(releaseAt, availableAt, shortageAt, net, quantity, null);
        }
    }

    public record CostBreakdown(
            String pricingMode,
            BigDecimal tierStart,
            BigDecimal purchaseCost,
            BigDecimal freightCost,
            BigDecimal processingCost,
            BigDecimal holdingCost,
            BigDecimal totalCost) {}

    public record LostSales(
            LocalDate week,
            BigDecimal observedDemand,
            BigDecimal inStockFraction,
            BigDecimal estimatedLostDemand,
            BigDecimal correctedDemand,
            String method) {}

    public record Finding(String reason, String reference, Instant at, BigDecimal quantity) {}

    public record ModelScore(
            String method, int folds, Double wape, double absoluteError, double biasUnits) {}

    public record Evaluation(
            LocalDate from, int weeks, double actual, double absoluteError, double biasUnits) {}

    public record Pattern(int weeks, int nonzeroWeeks, Double adi, Double cvSquared) {
        static Pattern of(List<Double> h) {
            var active =
                    h.stream()
                            .filter(n -> n > 0)
                            .mapToDouble(Double::doubleValue)
                            .summaryStatistics();
            if (active.getCount() == 0) return new Pattern(h.size(), 0, null, null);
            double mean = active.getAverage();
            double variance =
                    h.stream()
                            .filter(n -> n > 0)
                            .mapToDouble(n -> (n - mean) * (n - mean))
                            .average()
                            .orElse(0);
            return new Pattern(
                    h.size(),
                    (int) active.getCount(),
                    (double) h.size() / active.getCount(),
                    variance / (mean * mean));
        }
    }

    public record Result(
            StockTargets policy,
            Pattern pattern,
            String method,
            Evaluation holdout,
            List<ModelScore> backtest,
            List<LostSales> lostSales,
            List<Bucket> forecasts,
            List<Allocation> consumption,
            List<Point> projection,
            List<Proposal> proposals,
            List<Finding> exceptions) {}

    static LocalDate week(Instant at, ZoneId zone) {
        return at.atZone(zone)
                .toLocalDate()
                .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }

    private static Collection<Sale> known(List<Sale> sales, Instant cutoff) {
        Map<String, Sale> rows = new TreeMap<>();
        for (Sale row : sales) {
            if (!row.knownAt().isAfter(cutoff))
                rows.merge(row.line(), row, (a, b) -> a.revision() > b.revision() ? a : b);
        }
        return rows.values();
    }

    private static List<Double> history(
            PlanningInput s, LocalDate until, Instant cutoff, ZoneId zone) {
        int size = Math.toIntExact(ChronoUnit.WEEKS.between(s.historyStart(), until));
        double[] totals = new double[size];
        for (Sale sale : known(s.sales(), cutoff)) {
            long index = ChronoUnit.WEEKS.between(s.historyStart(), week(sale.due(), zone));
            if (index >= 0 && index < size) totals[(int) index] += sale.ordered().doubleValue();
        }
        return Arrays.stream(totals).boxed().toList();
    }

    private record CorrectedHistory(List<Double> values, List<LostSales> findings) {}

    private static CorrectedHistory correctedHistory(
            PlanningInput s, LocalDate until, Instant cutoff, ZoneId zone) {
        List<Double> raw = history(s, until, cutoff, zone);
        var values = new ArrayList<>(raw);
        var findings = new ArrayList<LostSales>();
        var signals = s.signals();
        if (signals == null || signals.availability() == null) {
            return new CorrectedHistory(values, findings);
        }
        for (AvailabilityObservation observation : signals.availability()) {
            int index = Math.toIntExact(ChronoUnit.WEEKS.between(s.historyStart(), observation.week()));
            if (index < 0 || index >= values.size()) continue;
            BigDecimal observed = BigDecimal.valueOf(values.get(index));
            PlanningChecks.require(
                    observed.compareTo(observation.observedDemand()) == 0,
                    "缺货观察销量须与同周已对账销量一致：" + observation.week());
            if (observation.inStockFraction().compareTo(BigDecimal.ONE) == 0) {
                findings.add(
                        new LostSales(
                                observation.week(),
                                observed,
                                observation.inStockFraction(),
                                ZERO,
                                observed,
                                "FULL_AVAILABILITY"));
                continue;
            }
            if (observation.inStockFraction().compareTo(new BigDecimal(".20")) < 0) {
                findings.add(
                        new LostSales(
                                observation.week(),
                                observed,
                                observation.inStockFraction(),
                                ZERO,
                                observed,
                                "LOW_AVAILABILITY_REVIEW"));
                continue;
            }
            BigDecimal uncensored =
                    observed.divide(observation.inStockFraction(), 6, RoundingMode.HALF_UP);
            if (observation.comparableDemand() != null) {
                uncensored = uncensored.min(observation.comparableDemand());
            }
            BigDecimal lost = uncensored.subtract(observed).max(ZERO);
            values.set(index, uncensored.doubleValue());
            findings.add(
                    new LostSales(
                            observation.week(),
                            observed,
                            observation.inStockFraction(),
                            lost,
                            uncensored,
                            observation.comparableDemand() == null
                                    ? "AVAILABILITY_UPLIFT"
                                    : "COMPARABLE_CAPPED_UPLIFT"));
        }
        return new CorrectedHistory(values, findings);
    }

    private static BigDecimal lifecycleForecast(
            LifecycleCurve curve, LocalDate forecastWeek, BigDecimal statisticalForecast) {
        int age = Math.toIntExact(ChronoUnit.WEEKS.between(curve.launchWeek(), forecastWeek));
        if (age < 0) return ZERO;
        BigDecimal anchorIndex = lifecycleIndex(curve.points(), curve.anchorAgeWeek());
        BigDecimal index = lifecycleIndex(curve.points(), age);
        if (curve.replaceStatisticalBaseline()) {
            return curve.anchorWeeklyDemand()
                    .multiply(index)
                    .divide(anchorIndex, 6, RoundingMode.HALF_UP);
        }
        return statisticalForecast.multiply(index).divide(anchorIndex, 6, RoundingMode.HALF_UP);
    }

    private static BigDecimal lifecycleIndex(List<LifecyclePoint> points, int age) {
        LifecyclePoint left = points.get(0);
        if (age <= left.ageWeek()) return left.index();
        for (int i = 1; i < points.size(); i++) {
            LifecyclePoint right = points.get(i);
            if (age <= right.ageWeek()) {
                BigDecimal distance = BigDecimal.valueOf(right.ageWeek() - left.ageWeek());
                BigDecimal progress =
                        BigDecimal.valueOf(age - left.ageWeek())
                                .divide(distance, 12, RoundingMode.HALF_UP);
                return left.index()
                        .add(right.index().subtract(left.index()).multiply(progress))
                        .setScale(6, RoundingMode.HALF_UP);
            }
            left = right;
        }
        return left.index();
    }

    public static Result calculate(PlanningInput s, Map<LocalDate, Adjustment> overrides) {
        PlanningChecks.require(s.assembly() == null, "配置展开需要已发布的车型族需求版本");
        return calculate(s, overrides, List.of(), List.of());
    }

    static Result calculate(
            PlanningInput s,
            Map<LocalDate, Adjustment> overrides,
            List<Requirement> componentDemand,
            List<Allocation> previousAllocations) {
        ZoneId zone = PlanningChecks.validate(s);
        LocalDate first = week(s.asOf(), zone);
        Instant end = first.plusWeeks(s.weeks()).atStartOfDay(zone).toInstant();
        CorrectedHistory corrected = correctedHistory(s, first, s.asOf(), zone);
        List<Double> history = corrected.values();
        List<ForecastModels.Fold> folds = new ArrayList<>();
        int protection = s.policy().protectionWeeks();
        int holdoutStart = history.size() - protection;
        for (int n = holdoutStart - protection; n >= 4 && folds.size() < 8; n -= protection) {
            LocalDate cut = s.historyStart().plusWeeks(n);
            folds.add(
                    new ForecastModels.Fold(
                            correctedHistory(
                                            s,
                                            cut,
                                            cut.atStartOfDay(zone).toInstant().minusNanos(1),
                                            zone)
                                    .values(),
                            history.subList(n, n + protection)));
        }
        var model = ForecastModels.select(folds);
        Evaluation holdout = null;
        if (holdoutStart >= 4) {
            LocalDate date = s.historyStart().plusWeeks(holdoutStart);
            var before =
                    correctedHistory(
                                    s, date, date.atStartOfDay(zone).toInstant().minusNanos(1), zone)
                            .values();
            double actual = 0, absolute = 0, bias = 0;
            for (int k = 0; k < protection; k++) {
                double observed = history.get(holdoutStart + k);
                double error =
                        ForecastModels.quantity(model.method(), before, k + 1).doubleValue()
                                - observed;
                actual += observed;
                absolute += Math.abs(error);
                bias += error;
            }
            holdout = new Evaluation(date, protection, actual, absolute, bias);
        }
        List<ModelScore> scores =
                model.scores().stream()
                        .map(
                                v ->
                                        new ModelScore(
                                                v.method().name(),
                                                v.folds(),
                                                v.wape(),
                                                v.absoluteError(),
                                                v.biasUnits()))
                        .toList();
        Map<LocalDate, BigDecimal> baseline = new LinkedHashMap<>(), total = new LinkedHashMap<>();
        for (int i = 0; i < s.weeks(); i++) {
            LocalDate bucket = first.plusWeeks(i);
            BigDecimal forecast = ForecastModels.quantity(model.method(), history, i + 1);
            LifecycleCurve lifecycle =
                    s.signals() == null ? null : s.signals().lifecycle();
            if (lifecycle != null) forecast = lifecycleForecast(lifecycle, bucket, forecast);
            baseline.put(bucket, forecast);
            Adjustment override = overrides.get(bucket);
            total.put(
                    bucket,
                    override != null && override.expiresAt().isAfter(s.asOf())
                            ? override.value()
                            : forecast);
        }
        Map<LocalDate, BigDecimal> remaining = new HashMap<>(total);
        NavigableMap<Instant, List<Flow>> flows = new TreeMap<>();
        flows.put(s.asOf(), new ArrayList<>());
        List<Allocation> allocations = new ArrayList<>();
        var orders =
                known(s.sales(), s.asOf()).stream()
                        .sorted(Comparator.comparing(Sale::due).thenComparing(Sale::line))
                        .toList();
        // Carry future-bucket allocations of past-due lines across a weekly rollover.
        // A cancellation caps the carry; a changed due week is matched afresh.
        Map<String, List<Allocation>> carry = new HashMap<>();
        for (var allocation : previousAllocations) {
            if (total.containsKey(allocation.week()))
                carry.computeIfAbsent(allocation.line(), k -> new ArrayList<>()).add(allocation);
        }
        for (var sale : orders) {
            if (!week(sale.due(), zone).isBefore(first)) continue;
            BigDecimal left = sale.ordered();
            for (var prior : carry.getOrDefault(sale.line(), List.of())) {
                if (!prior.originalWeek().equals(week(sale.due(), zone))) continue;
                BigDecimal capped = left.min(prior.quantity());
                BigDecimal unused =
                        consume(
                                sale,
                                prior.originalWeek(),
                                prior.week(),
                                capped,
                                remaining,
                                allocations);
                left = left.subtract(capped.subtract(unused));
            }
        }
        // Reserve own-week forecast for all lines before opening neighboring buckets.
        Map<String, BigDecimal> unconsumed = new HashMap<>();
        for (Sale sale : orders) {
            LocalDate bucket = week(sale.due(), zone);
            if (total.containsKey(bucket)) {
                unconsumed.put(
                        sale.line(),
                        consume(sale, bucket, bucket, sale.ordered(), remaining, allocations));
            }
            Instant due = sale.due().isBefore(s.asOf()) ? s.asOf() : sale.due();
            add(flows, end, due, "SALE", sale.line(), sale.shipped().subtract(sale.ordered()));
        }
        for (Sale sale : orders) {
            BigDecimal left = unconsumed.getOrDefault(sale.line(), ZERO);
            LocalDate bucket = week(sale.due(), zone);
            for (int distance = 1;
                    left.signum() > 0 && distance <= s.policy().nettingWeeks();
                    distance++) {
                left =
                        consume(
                                sale,
                                bucket,
                                bucket.minusWeeks(distance),
                                left,
                                remaining,
                                allocations);
                left =
                        consume(
                                sale,
                                bucket,
                                bucket.plusWeeks(distance),
                                left,
                                remaining,
                                allocations);
            }
        }
        List<Bucket> forecasts = new ArrayList<>();
        for (var entry : total.entrySet()) {
            LocalDate date = entry.getKey();
            BigDecimal net = remaining.get(date);
            forecasts.add(
                    new Bucket(
                            date,
                            baseline.get(date),
                            entry.getValue(),
                            entry.getValue().subtract(net),
                            net));
            // Conservative convention for undated weekly demand: available by the start of its
            // bucket.
            Instant at = date.atStartOfDay(zone).toInstant();
            add(
                    flows,
                    end,
                    at.isBefore(s.asOf()) ? s.asOf() : at,
                    "FORECAST",
                    date.toString(),
                    net.negate());
        }
        List<Finding> exceptions = new ArrayList<>();
        for (Supply supply : s.supplies()) {
            BigDecimal open =
                    supply.ordered().subtract(supply.received()).subtract(supply.cancelled());
            if (open.signum() == 0) continue;
            if (supply.availableAt().isBefore(s.asOf())) {
                exceptions.add(new Finding("STALE_ETA", supply.line(), supply.availableAt(), open));
            } else add(flows, end, supply.availableAt(), "SUPPLY", supply.line(), open);
        }
        for (Requirement r : s.requirements()) {
            add(
                    flows,
                    end,
                    r.requiredAt().isBefore(s.asOf()) ? s.asOf() : r.requiredAt(),
                    "COMPONENT",
                    r.line(),
                    r.quantity().negate());
        }
        for (Requirement r : componentDemand)
            add(flows, end, r.requiredAt(), "BOM", r.line(), r.quantity().negate());
        List<Window> windows =
                s.windows().stream().filter(w -> w.availableAt().isBefore(end)).toList();
        windows.forEach(w -> flows.computeIfAbsent(w.availableAt(), k -> new ArrayList<>()));
        NavigableMap<Instant, BigDecimal> confirmed = new TreeMap<>();
        BigDecimal balance = s.opening();
        for (var e : flows.entrySet()) {
            for (Flow f : e.getValue()) balance = balance.add(f.change());
            confirmed.put(e.getKey(), balance);
        }
        StockTargets target = StockTargets.derive(s);
        boolean inactive =
                history.size() >= 26
                        && history.subList(history.size() - 26, history.size()).stream()
                                .allMatch(n -> n == 0)
                        && flows.values().stream()
                                .flatMap(List::stream)
                                .noneMatch(f -> f.change().signum() < 0);
        if (inactive) exceptions.add(new Finding("INACTIVE_REVIEW", s.material(), s.asOf(), ZERO));
        List<Proposal> proposals =
                inactive
                        ? List.of()
                        : propose(
                                s.policy(),
                                s.signals() == null ? null : s.signals().costs(),
                                target,
                                confirmed,
                                windows,
                                end,
                                exceptions);
        Map<Instant, BigDecimal> receipts = new HashMap<>();
        proposals.forEach(p -> receipts.put(p.availableAt(), p.quantity()));
        List<Point> points = new ArrayList<>();
        BigDecimal simulated = ZERO;
        for (var e : confirmed.entrySet()) {
            simulated = simulated.add(receipts.getOrDefault(e.getKey(), ZERO));
            BigDecimal scenario = e.getValue().add(simulated);
            if (scenario.compareTo(target.safety()) < 0)
                exceptions.add(
                        new Finding(
                                scenario.signum() < 0 ? "SHORTAGE" : "BELOW_SAFETY",
                                s.material(),
                                e.getKey(),
                                target.safety().subtract(scenario)));
            points.add(new Point(e.getKey(), flows.get(e.getKey()), e.getValue(), scenario));
        }
        return new Result(
                target,
                Pattern.of(history),
                (s.signals() != null && s.signals().lifecycle() != null
                                ? "LIFECYCLE_" + model.method().name()
                                : model.method().name()),
                holdout,
                scores,
                corrected.findings(),
                forecasts,
                allocations,
                points,
                proposals,
                exceptions);
    }

    private static BigDecimal consume(
            Sale sale,
            LocalDate originalWeek,
            LocalDate week,
            BigDecimal left,
            Map<LocalDate, BigDecimal> remaining,
            List<Allocation> allocations) {
        BigDecimal used = remaining.getOrDefault(week, ZERO).min(left);
        if (used.signum() > 0) {
            remaining.put(week, remaining.get(week).subtract(used));
            allocations.add(new Allocation(sale.line(), sale.revision(), originalWeek, week, used));
        }
        return left.subtract(used);
    }

    private static void add(
            Map<Instant, List<Flow>> flows,
            Instant end,
            Instant at,
            String type,
            String id,
            BigDecimal delta) {
        if (at.isBefore(end) && delta.signum() != 0)
            flows.computeIfAbsent(at, k -> new ArrayList<>()).add(new Flow(type, id, delta));
    }

    private static List<Proposal> propose(
            Policy p,
            CostProfile costs,
            StockTargets target,
            NavigableMap<Instant, BigDecimal> confirmed,
            List<Window> windows,
            Instant end,
            List<Finding> exceptions) {
        List<Proposal> proposals = new ArrayList<>();
        if (!p.purchased()) return proposals;
        BigDecimal prior = ZERO;
        BigDecimal priorCost = ZERO;
        for (int i = 0; i < windows.size(); i++) {
            Window window = windows.get(i);
            Instant until = i + 1 == windows.size() ? end : windows.get(i + 1).availableAt();
            BigDecimal need = ZERO;
            Instant firstShort = null;
            for (var entry :
                    confirmed.subMap(window.availableAt(), true, until, false).entrySet()) {
                BigDecimal gap = target.safety().subtract(entry.getValue().add(prior));
                if (gap.signum() > 0 && firstShort == null) firstShort = entry.getKey();
                need = need.max(gap);
            }
            if (need.signum() == 0) continue;
            BigDecimal minimum = need.max(p.minimum()).max(target.economicLot());
            var candidates = commercialCandidates(minimum, p.multiple(), costs);
            Candidate selected = null;
            for (BigDecimal qty : candidates) {
                if (p.maximumOrder() != null && qty.compareTo(p.maximumOrder()) > 0) continue;
                if (p.maximumOnHand() != null) {
                    BigDecimal peak =
                            Collections.max(confirmed.tailMap(window.availableAt(), true).values())
                                    .add(prior)
                                    .add(qty);
                    if (peak.compareTo(p.maximumOnHand()) > 0) continue;
                }
                CostBreakdown cost = totalCost(qty, need, p, costs);
                if (p.purchaseBudget() != null) {
                    BigDecimal currentCost =
                            cost == null ? qty.multiply(p.unitCost()) : cost.totalCost();
                    if (priorCost.add(currentCost).compareTo(p.purchaseBudget()) > 0) continue;
                }
                Candidate candidate = new Candidate(qty, cost);
                if (selected == null || candidate.costForSort().compareTo(selected.costForSort()) < 0)
                    selected = candidate;
            }
            if (selected == null) {
                BigDecimal first = candidates.get(0);
                if (p.maximumOrder() != null && first.compareTo(p.maximumOrder()) > 0)
                    exceptions.add(new Finding("ORDER_LIMIT", p.supplier(), firstShort, first));
                else if (p.maximumOnHand() != null)
                    exceptions.add(new Finding("STORAGE_CAPACITY", p.supplier(), firstShort, first));
                else exceptions.add(new Finding("PURCHASE_BUDGET", p.supplier(), firstShort, first));
                continue;
            }
            proposals.add(
                    new Proposal(
                            window.releaseAt(),
                            window.availableAt(),
                            firstShort,
                            need,
                            selected.quantity(),
                            selected.cost()));
            prior = prior.add(selected.quantity());
            if (selected.cost() != null) priorCost = priorCost.add(selected.cost().totalCost());
            else if (p.unitCost() != null)
                priorCost = priorCost.add(selected.quantity().multiply(p.unitCost()));
        }
        return proposals;
    }

    private record Candidate(BigDecimal quantity, CostBreakdown cost) {
        BigDecimal costForSort() {
            return cost == null ? quantity : cost.totalCost();
        }
    }

    private static List<BigDecimal> commercialCandidates(
            BigDecimal minimum, BigDecimal multiple, CostProfile costs) {
        var quantities = new TreeSet<BigDecimal>();
        quantities.add(roundPurchase(minimum, multiple));
        if (costs != null) {
            for (PriceTier tier : costs.tiers()) {
                if (tier.fromQuantity().compareTo(minimum) >= 0)
                    quantities.add(roundPurchase(tier.fromQuantity(), multiple));
            }
        }
        return List.copyOf(quantities);
    }

    private static BigDecimal roundPurchase(BigDecimal amount, BigDecimal multiple) {
        return amount.divide(multiple, 0, RoundingMode.CEILING).multiply(multiple);
    }

    private static CostBreakdown totalCost(
            BigDecimal quantity, BigDecimal need, Policy policy, CostProfile costs) {
        if (costs == null) return null;
        PriceTier applied =
                costs.tiers().stream()
                        .filter(tier -> tier.fromQuantity().compareTo(quantity) <= 0)
                        .reduce((left, right) -> right)
                        .orElseThrow();
        BigDecimal purchase =
                "ALL_UNITS".equals(costs.pricingMode())
                        ? quantity.multiply(applied.unitPrice())
                        : incrementalPurchaseCost(quantity, costs.tiers());
        BigDecimal freight = costs.freightFixedCost();
        BigDecimal processing =
                costs.processingFixedCost()
                        .add(costs.processingUnitCost().multiply(quantity));
        BigDecimal holding =
                quantity.subtract(need)
                        .max(ZERO)
                        .multiply(costs.holdingCostPerUnitWeek())
                        .multiply(BigDecimal.valueOf(costs.excessHoldingWeeks()));
        return new CostBreakdown(
                costs.pricingMode(),
                applied.fromQuantity(),
                purchase,
                freight,
                processing,
                holding,
                purchase.add(freight).add(processing).add(holding));
    }

    private static BigDecimal incrementalPurchaseCost(
            BigDecimal quantity, List<PriceTier> tiers) {
        BigDecimal total = ZERO;
        for (int i = 0; i < tiers.size(); i++) {
            PriceTier tier = tiers.get(i);
            BigDecimal upper =
                    i + 1 == tiers.size() ? quantity : tiers.get(i + 1).fromQuantity().subtract(BigDecimal.ONE);
            BigDecimal units = quantity.min(upper).subtract(tier.fromQuantity()).add(BigDecimal.ONE);
            if (units.signum() > 0) total = total.add(units.multiply(tier.unitPrice()));
        }
        return total;
    }

    static Result awaitingReconciliation(Result result, String purchaseId, Instant at) {
        var issues = new ArrayList<>(result.exceptions());
        issues.add(new Finding("PURCHASE_PENDING_RECONCILIATION", purchaseId, at, ZERO));
        var points =
                result.projection().stream()
                        .map(p -> new Point(p.at(), p.flows(), p.confirmed(), p.confirmed()))
                        .toList();
        return new Result(
                result.policy(),
                result.pattern(),
                result.method(),
                result.holdout(),
                result.backtest(),
                result.lostSales(),
                result.forecasts(),
                result.consumption(),
                points,
                List.of(),
                issues);
    }

    private DemandPlanner() {}
}
