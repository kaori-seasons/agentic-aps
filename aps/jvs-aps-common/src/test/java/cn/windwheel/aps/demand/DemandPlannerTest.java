package cn.windwheel.aps.demand;

import static cn.windwheel.aps.demand.PlanningInput.*;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;

class DemandPlannerTest {
    private static final Instant MON = Instant.parse("2026-11-16T00:00:00Z");
    private static final LocalDate WEEK = LocalDate.parse("2026-11-16");

    private static BigDecimal n(long n) {
        return BigDecimal.valueOf(n);
    }

    private static Instant day(int i) {
        return MON.plus(Duration.ofDays(i));
    }

    private static Sale sale(String id, int date, int qty, int shipped) {
        return new Sale(id, 1, MON, day(date), n(qty), n(shipped));
    }

    private static PlanningInput image(
            List<Sale> sales,
            List<Supply> supplies,
            List<Requirement> requirements,
            BigDecimal cap) {
        return new PlanningInput(
                "WH",
                "PUMP",
                "ea",
                1,
                MON,
                "UTC",
                WEEK,
                1,
                n(14),
                new Policy("S", true, n(3), n(10), n(4), cap, 0, 1),
                sales,
                supplies,
                requirements,
                List.of(new Window(MON, day(2))),
                null,
                null);
    }

    private static DemandPlanner.Result sample(int eta, BigDecimal cap) {
        return DemandPlanner.calculate(
                image(
                        List.of(sale("1", 0, 6, 0), sale("2", 1, 12, 0), sale("3", 2, 7, 0)),
                        List.of(new Supply("PO1", day(eta), n(10), n(4), n(0))),
                        List.of(),
                        cap),
                Map.of());
    }

    @Test
    void earlyAndLateReceiptsRespectAvailableTimeAndPackSize() {
        var early = sample(1, null);
        assertEquals(
                List.of(n(8), n(2), n(-5)),
                early.projection().stream().map(DemandPlanner.Point::confirmed).toList());
        assertEquals(n(12), early.proposals().get(0).quantity());
        assertEquals(n(7), early.projection().get(2).scenario());
        var late = sample(3, null);
        assertEquals(n(-4), late.projection().get(1).confirmed());
        assertEquals(n(16), late.proposals().get(0).quantity());
    }

    @Test
    void conflictsAreVisibleAndNeverRoundedDown() {
        var r = sample(1, n(11));
        assertTrue(r.proposals().isEmpty());
        assertTrue(r.exceptions().stream().anyMatch(e -> e.reason().equals("ORDER_LIMIT")));
    }

    @Test
    void confirmedOrdersConsumeForecastEvenAfterPartialDelivery() {
        var input = image(List.of(sale("A", 1, 70, 20)), List.of(), List.of(), null);
        var r =
                DemandPlanner.calculate(
                        input, Map.of(WEEK, new DemandPlanner.Adjustment(n(100), "批准", day(8))));
        assertEquals(n(30), r.forecasts().get(0).net());
        assertEquals(n(70), r.consumption().get(0).quantity());
        assertEquals(n(-66), r.projection().get(r.projection().size() - 1).confirmed());
    }

    @Test
    void cancellationReplacesOlderRevisionAndExpiredEtaCannotSupply() {
        var input =
                image(
                        List.of(sale("A", 1, 70, 20), new Sale("A", 2, MON, day(1), n(20), n(20))),
                        List.of(new Supply("late", day(-1), n(10), n(0), n(0))),
                        List.of(),
                        null);
        var r =
                DemandPlanner.calculate(
                        input, Map.of(WEEK, new DemandPlanner.Adjustment(n(0), "停用", day(1))));
        assertEquals(n(14), r.projection().get(0).confirmed());
        assertEquals("STALE_ETA", r.exceptions().get(0).reason());
        assertTrue(r.consumption().isEmpty());
    }

    @Test
    void repeatedOrFutureInputIsRejected() {
        var line = sale("A", 1, 1, 0);
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        DemandPlanner.calculate(
                                image(List.of(line, line), List.of(), List.of(), null), Map.of()));
        var late = new Sale("A", 2, day(1), day(1), n(1), n(0));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        DemandPlanner.calculate(
                                image(List.of(late), List.of(), List.of(), null), Map.of()));
    }

    @Test
    void intradayShortageSurvivesLaterReceipt() {
        var input =
                image(
                        List.of(sale("A", 1, 19, 0)),
                        List.of(new Supply("later", day(1).plusSeconds(1), n(10), n(0), n(0))),
                        List.of(),
                        null);
        var r = DemandPlanner.calculate(input, Map.of());
        assertTrue(
                r.exceptions().stream()
                        .anyMatch(e -> e.reason().equals("SHORTAGE") && e.at().equals(day(1))));
    }

    @Test
    void variantsShareAssemblyInventoryAndCarrySalesLineProvenance() {
        var family = image(List.of(sale("A", 0, 4, 0)), List.of(), List.of(), null);
        var parent =
                DemandPlanner.calculate(
                        family, Map.of(WEEK, new DemandPlanner.Adjustment(n(10), "销售评审", day(7))));
        var mix = Map.of("short", new BigDecimal(".5"), "tall", new BigDecimal(".5"));
        var parts =
                Map.of(
                        "short",
                        List.of(new ConfiguredAssembly.Part("kit", n(1))),
                        "tall",
                        List.of(new ConfiguredAssembly.Part("kit", n(1))),
                        "kit",
                        List.of(new ConfiguredAssembly.Part("pump", n(2))));
        var assembly =
                new ConfiguredAssembly(
                        "run1",
                        "v1",
                        MON,
                        day(7),
                        Map.of("A", "short"),
                        mix,
                        Map.of("kit", n(3)),
                        parts);
        var needs = assembly.requirements(parent, "pump");
        // Four firm trucks consume three available kits. Six uncovered trucks require twelve pumps.
        assertEquals(2, needs.size());
        assertTrue(needs.get(0).line().endsWith("/SALE/A"));
        assertEquals(0, n(2).compareTo(needs.get(0).quantity()));
        assertEquals(0, n(12).compareTo(needs.get(1).quantity()));
        var cyclic =
                new ConfiguredAssembly(
                        "run",
                        "bad",
                        MON,
                        day(7),
                        Map.of(),
                        Map.of("kit", n(1)),
                        Map.of(),
                        Map.of("kit", List.of(new ConfiguredAssembly.Part("kit", n(1)))));
        assertThrows(IllegalArgumentException.class, cyclic::validate);
    }

    @Test
    void empiricalProtectionTargetAndEconomicLotUseConsistentUnits() {
        var base = image(List.of(), List.of(), List.of(), null);
        var obs = new ArrayList<PlanningInput.Observation>();
        for (int i = 0; i < 20; i++)
            obs.add(new PlanningInput.Observation(day(-i - 1), n(10 + i), n(10)));
        var samples =
                new PlanningInput.PolicySamples(new BigDecimal(".90"), obs, n(100), n(5), n(1));
        var input =
                new PlanningInput(
                        base.location(),
                        base.material(),
                        base.unit(),
                        1,
                        MON,
                        "UTC",
                        WEEK,
                        1,
                        n(14),
                        base.policy(),
                        List.of(),
                        List.of(),
                        List.of(),
                        base.windows(),
                        null,
                        samples);
        var r = DemandPlanner.calculate(input, Map.of());
        assertEquals(n(17), r.policy().safety());
        assertEquals(n(32), r.proposals().get(0).quantity());
    }

    @Test
    void selectionRequiresThreeComparableRollingOrigins() {
        var shortHistory =
                ForecastModels.select(List.of(new ForecastModels.Fold(List.of(1d), List.of(2d))));
        assertEquals(ForecastModels.Method.MEAN4, shortHistory.method());
        var folds =
                Collections.nCopies(
                        4, new ForecastModels.Fold(List.of(2d, 2d, 2d, 2d), List.of(2d, 2d)));
        var best = ForecastModels.select(folds);
        assertTrue(best.scores().stream().allMatch(score -> score.folds() == 4));
        assertEquals(0d, best.scores().get(0).wape());
    }

    @Test
    void previousConsumptionOfAFutureBucketSurvivesWeekChangeAndPartialShipment() {
        var current = image(List.of(sale("old", -3, 70, 20)), List.of(), List.of(), null);
        var carry =
                List.of(new DemandPlanner.Allocation("old", 1, WEEK.minusWeeks(1), WEEK, n(30)));
        var result =
                DemandPlanner.calculate(
                        current,
                        Map.of(WEEK, new DemandPlanner.Adjustment(n(100), "已批", day(3))),
                        List.of(),
                        carry);
        assertEquals(n(70), result.forecasts().get(0).net());
        assertEquals(n(30), result.consumption().get(0).quantity());
    }

    @Test
    void orderRoundedToPackMustFitBudgetAndCapacity() {
        var base = image(List.of(sale("A", 2, 22, 0)), List.of(), List.of(), null);
        for (boolean budget : List.of(true, false)) {
            var policy =
                    new Policy(
                            "supplier",
                            true,
                            n(3),
                            n(10),
                            n(4),
                            null,
                            0,
                            1,
                            budget ? null : n(2),
                            budget ? n(2) : null,
                            budget ? n(20) : null);
            var input =
                    new PlanningInput(
                            "WH",
                            "PUMP",
                            "ea",
                            1,
                            MON,
                            "UTC",
                            WEEK,
                            1,
                            n(14),
                            policy,
                            base.sales(),
                            List.of(),
                            List.of(),
                            base.windows(),
                            null,
                            null);
            var result = DemandPlanner.calculate(input, Map.of());
            assertTrue(result.proposals().isEmpty());
            assertTrue(
                    result.exceptions().stream()
                            .anyMatch(
                                    e ->
                                            e.reason()
                                                    .equals(
                                                            budget
                                                                    ? "PURCHASE_BUDGET"
                                                                    : "STORAGE_CAPACITY")));
        }
    }

    @Test
    void backtestFoldsNeverReadALateRecordedRevision() {
        var sales = new ArrayList<Sale>();
        for (int i = 1; i <= 15; i++) {
            sales.add(new Sale("history" + i, 1, day(-i * 7 - 1), day(-i * 7), n(10), n(10)));
            sales.add(new Sale("history" + i, 2, MON, day(-i * 7), n(20), n(20)));
        }
        var base = image(sales, List.of(), List.of(), null);
        var input =
                new PlanningInput(
                        "WH",
                        "PUMP",
                        "ea",
                        1,
                        MON,
                        "UTC",
                        WEEK.minusWeeks(15),
                        1,
                        n(14),
                        base.policy(),
                        sales,
                        List.of(),
                        List.of(),
                        base.windows(),
                        null,
                        null);
        var result = DemandPlanner.calculate(input, Map.of());
        assertEquals(8, result.backtest().get(0).folds());
        assertEquals(10d, result.holdout().absoluteError());
        assertEquals(0, n(20).compareTo(result.forecasts().get(0).baseline()));
    }
}
