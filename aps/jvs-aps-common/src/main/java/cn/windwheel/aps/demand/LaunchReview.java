package cn.windwheel.aps.demand;

import static cn.windwheel.aps.demand.PlanningChecks.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.*;
import java.util.*;

/** Demand-only evidence and independent, sealed expert rounds for a single demand week. */
public final class LaunchReview {
    private LaunchReview() {}

    public record Analog(
            String product,
            int observedDays,
            int targetDays,
            BigDecimal sales,
            boolean complete,
            boolean stockout,
            boolean promotion,
            String explanation) {}

    public record Evidence(
            List<Analog> analogs, BigDecimal weeklyReference, List<String> warnings) {}

    public record Vote(
            BigDecimal low,
            BigDecimal expected,
            BigDecimal high,
            String reason,
            String informationNeeded) {}

    public record Summary(int count, BigDecimal minimum, BigDecimal median, BigDecimal maximum) {}

    public record Request(
            String location,
            String material,
            LocalDate week,
            long generation,
            Instant expiresAt,
            String context,
            List<String> experts,
            List<Analog> analogs) {}

    public record Round(int number, boolean closed, Map<String, Vote> votes, String feedback) {}

    public record State(
            Request request, String owner, Evidence evidence, List<Round> rounds, String draftId) {}

    public static Evidence evidence(List<Analog> analogs) {
        require(analogs != null && !analogs.isEmpty() && analogs.size() <= 50, "提供1至50个类似产品");
        List<BigDecimal> eligible = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        Set<String> products = new HashSet<>();
        for (Analog a : analogs) {
            require(a != null, "参考产品缺失");
            key(a.product());
            require(products.add(a.product()), "参考产品重复");
            quantity(a.sales());
            require(
                    a.observedDays() > 0
                            && a.observedDays() <= a.targetDays()
                            && a.targetDays() <= 366,
                    "观察天数须在1至目标天数之间，目标天数最多366");
            text(a.explanation(), "参考产品说明");
            if (!a.complete()
                    || a.observedDays() != a.targetDays()
                    || a.stockout()
                    || a.promotion()) {
                warnings.add(a.product() + "：观察期不足、缺货或促销，保留证据供专家判断");
            } else
                eligible.add(
                        a.sales()
                                .multiply(BigDecimal.valueOf(7))
                                .divide(
                                        BigDecimal.valueOf(a.observedDays()),
                                        6,
                                        RoundingMode.HALF_UP));
        }
        return new Evidence(
                List.copyOf(analogs), eligible.isEmpty() ? null : median(eligible), warnings);
    }

    public static void validate(Vote vote) {
        require(vote != null, "判断缺失");
        quantity(vote.low());
        quantity(vote.expected());
        quantity(vote.high());
        require(
                vote.low().compareTo(vote.expected()) <= 0
                        && vote.expected().compareTo(vote.high()) <= 0,
                "需求判断须满足下限≤最可能值≤上限");
        text(vote.reason(), "判断依据");
        text(vote.informationNeeded(), "补充信息");
    }

    static void text(String value, String label) {
        require(value != null && !value.isBlank() && value.length() <= 2000, label + "须为1至2000字");
    }

    public static Summary summarize(Collection<Vote> votes) {
        require(votes.size() >= 3, "至少三位专家提交后才能汇总");
        var values = votes.stream().map(Vote::expected).sorted().toList();
        return new Summary(
                values.size(), values.get(0), median(values), values.get(values.size() - 1));
    }

    private static BigDecimal median(List<BigDecimal> input) {
        var values = input.stream().sorted().toList();
        int n = values.size();
        return n % 2 == 1
                ? values.get(n / 2)
                : values.get(n / 2 - 1)
                        .add(values.get(n / 2))
                        .divide(BigDecimal.valueOf(2), 6, RoundingMode.HALF_UP);
    }
}
