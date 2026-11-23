package cn.windwheel.aps.demand;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class DemandSignalsTest {
    private static final Instant AS_OF = Instant.parse("2026-11-16T00:00:00Z");
    private static final LocalDate WEEK = LocalDate.parse("2026-11-16");

    private static BigDecimal n(String value) {
        return new BigDecimal(value);
    }

    private PlanningInput input(
            List<PlanningInput.Sale> sales, PlanningInput.DemandSignals signals) {
        return new PlanningInput(
                "WH",
                "LAUNCH",
                "ea",
                1,
                AS_OF,
                "UTC",
                WEEK.minusWeeks(4),
                2,
                BigDecimal.ZERO,
                new PlanningInput.Policy(
                        "supplier", true, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ONE, null, 0, 1),
                sales,
                List.of(),
                List.of(),
                List.of(new PlanningInput.Window(AS_OF, AS_OF.plusSeconds(3600))),
                null,
                null,
                signals);
    }

    @Test
    void stockoutObservationUsesComparableCapAndIsAuditable() {
        LocalDate affected = WEEK.minusWeeks(1);
        var sales =
                List.of(
                        new PlanningInput.Sale(
                                "closed-week",
                                1,
                                AS_OF.minusSeconds(1),
                                affected.atStartOfDay(java.time.ZoneOffset.UTC).toInstant(),
                                n("50"),
                                n("0")));
        var availability =
                new PlanningInput.AvailabilityObservation(affected, n("50"), n(".5"), n("80"));
        var result =
                DemandPlanner.calculate(
                        input(sales, new PlanningInput.DemandSignals(null, List.of(availability), null)),
                        Map.of());
        assertEquals(0, n("30").compareTo(result.lostSales().get(0).estimatedLostDemand()));
        assertEquals(0, n("80").compareTo(result.lostSales().get(0).correctedDemand()));
        assertEquals("COMPARABLE_CAPPED_UPLIFT", result.lostSales().get(0).method());
    }

    @Test
    void lowAvailabilityDoesNotInventDemandWithoutReview() {
        LocalDate affected = WEEK.minusWeeks(1);
        var sales =
                List.of(
                        new PlanningInput.Sale(
                                "closed-week",
                                1,
                                AS_OF.minusSeconds(1),
                                affected.atStartOfDay(java.time.ZoneOffset.UTC).toInstant(),
                                n("50"),
                                n("0")));
        var observation =
                new PlanningInput.AvailabilityObservation(affected, n("50"), n(".19"), n("100"));
        var result =
                DemandPlanner.calculate(
                        input(sales, new PlanningInput.DemandSignals(null, List.of(observation), null)),
                        Map.of());
        assertEquals("LOW_AVAILABILITY_REVIEW", result.lostSales().get(0).method());
        assertEquals(BigDecimal.ZERO, result.lostSales().get(0).estimatedLostDemand());
    }

    @Test
    void lifecycleCurveReplacesSparseBaselineAndShapesFutureWeeks() {
        var curve =
                new PlanningInput.LifecycleCurve(
                        WEEK,
                        0,
                        n("100"),
                        true,
                        List.of(
                                new PlanningInput.LifecyclePoint(0, BigDecimal.ONE),
                                new PlanningInput.LifecyclePoint(1, n("2"))));
        var result =
                DemandPlanner.calculate(
                        input(List.of(), new PlanningInput.DemandSignals(curve, List.of(), null)),
                        Map.of());
        assertEquals("LIFECYCLE_MEAN4", result.method());
        assertEquals(0, n("100").compareTo(result.forecasts().get(0).baseline()));
        assertEquals(0, n("200").compareTo(result.forecasts().get(1).baseline()));
    }

    @Test
    void allUnitsPriceBreakCanBeatSmallerRequiredPurchaseAfterTotalCostComparison() {
        var terms =
                new PlanningInput.CostProfile(
                        "ALL_UNITS",
                        List.of(
                                new PlanningInput.PriceTier(BigDecimal.ONE, n("12")),
                                new PlanningInput.PriceTier(n("100"), n("8"))),
                        n("20"),
                        BigDecimal.ZERO,
                        BigDecimal.ZERO,
                        BigDecimal.ZERO,
                        0);
        var result =
                DemandPlanner.calculate(
                        input(
                                List.of(),
                                new PlanningInput.DemandSignals(null, List.of(), terms)),
                        Map.of(WEEK, new DemandPlanner.Adjustment(n("80"), "approved", AS_OF.plusSeconds(86_400))));
        var proposal = result.proposals().get(0);
        assertEquals(0, n("100").compareTo(proposal.quantity()));
        assertEquals("ALL_UNITS", proposal.cost().pricingMode());
        assertEquals(0, n("820").compareTo(proposal.cost().totalCost()));
    }
}
