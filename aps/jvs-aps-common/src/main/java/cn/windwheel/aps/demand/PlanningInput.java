package cn.windwheel.aps.demand;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** One reconciled location/material image. Quantity fields use the material base unit. */
public record PlanningInput(
        String location,
        String material,
        String unit,
        long revision,
        Instant asOf,
        String zone,
        LocalDate historyStart,
        int weeks,
        BigDecimal opening,
        Policy policy,
        List<Sale> sales,
        List<Supply> supplies,
        List<Requirement> requirements,
        List<Window> windows,
        ConfiguredAssembly assembly,
        PolicySamples samples,
        DemandSignals signals) {
    public PlanningInput(
            String location,
            String material,
            String unit,
            long revision,
            Instant asOf,
            String zone,
            LocalDate historyStart,
            int weeks,
            BigDecimal opening,
            Policy policy,
            List<Sale> sales,
            List<Supply> supplies,
            List<Requirement> requirements,
            List<Window> windows,
            ConfiguredAssembly assembly,
            PolicySamples samples) {
        this(
                location,
                material,
                unit,
                revision,
                asOf,
                zone,
                historyStart,
                weeks,
                opening,
                policy,
                sales,
                supplies,
                requirements,
                windows,
                assembly,
                samples,
                null);
    }

    PlanningInput withSales(List<Sale> rows) {
        return new PlanningInput(
                location,
                material,
                unit,
                revision,
                asOf,
                zone,
                historyStart,
                weeks,
                opening,
                policy,
                rows,
                supplies,
                requirements,
                windows,
                assembly,
                samples,
                signals);
    }

    /**
     * Historical protection-period outcomes, measured in the same unit and known at the snapshot
     * cutoff.
     */
    public record Observation(Instant knownAt, BigDecimal actual, BigDecimal forecast) {}

    public record PolicySamples(
            BigDecimal serviceLevel,
            List<Observation> observations,
            BigDecimal annualDemand,
            BigDecimal orderCost,
            BigDecimal annualHoldingCostPerUnit) {}

    /** Absolute order-line state after a source revision, including cumulative shipped quantity. */
    public record Sale(
            String line,
            long revision,
            Instant knownAt,
            Instant due,
            BigDecimal ordered,
            BigDecimal shipped) {}

    /** A unique delivery schedule line, including accepted and cancelled cumulative quantities. */
    public record Supply(
            String line,
            Instant availableAt,
            BigDecimal ordered,
            BigDecimal received,
            BigDecimal cancelled) {}

    /**
     * Additional gross component requirements (net of dedicated reservation coverage), pegged to
     * their origin.
     */
    public record Requirement(String line, Instant requiredAt, BigDecimal quantity) {}

    /**
     * Released by the approved supplier calendar; availableAt already includes receiving buffer.
     */
    public record Window(Instant releaseAt, Instant availableAt) {}

    /**
     * Optional auditable evidence used for lifecycle demand, stockout correction, and total-cost
     * purchasing. Missing signals retain the established forecast and replenishment behavior.
     */
    public record DemandSignals(
            LifecycleCurve lifecycle,
            List<AvailabilityObservation> availability,
            CostProfile costs) {}

    /**
     * A weekly lifecycle curve. anchorWeeklyDemand is the known or approved demand at
     * anchorAgeWeek. Curves can replace a sparse statistical baseline for a new launch, or scale
     * a mature-product statistical baseline by lifecycle index.
     */
    public record LifecycleCurve(
            LocalDate launchWeek,
            int anchorAgeWeek,
            BigDecimal anchorWeeklyDemand,
            boolean replaceStatisticalBaseline,
            List<LifecyclePoint> points) {}

    public record LifecyclePoint(int ageWeek, BigDecimal index) {}

    /**
     * Historical realised demand and availability for one closed week. comparableDemand is an
     * optional business cap for the demand that could have been realised without the stockout.
     */
    public record AvailabilityObservation(
            LocalDate week,
            BigDecimal observedDemand,
            BigDecimal inStockFraction,
            BigDecimal comparableDemand) {}

    /** Commercial terms applied only when the plan selects a purchase proposal. */
    public record CostProfile(
            String pricingMode,
            List<PriceTier> tiers,
            BigDecimal freightFixedCost,
            BigDecimal processingFixedCost,
            BigDecimal processingUnitCost,
            BigDecimal holdingCostPerUnitWeek,
            int excessHoldingWeeks) {}

    public record PriceTier(BigDecimal fromQuantity, BigDecimal unitPrice) {}

    public record Policy(
            String supplier,
            boolean purchased,
            BigDecimal safety,
            BigDecimal minimum,
            BigDecimal multiple,
            BigDecimal maximumOrder,
            int nettingWeeks,
            int protectionWeeks,
            BigDecimal maximumOnHand,
            BigDecimal unitCost,
            BigDecimal purchaseBudget) {
        public Policy(
                String supplier,
                boolean purchased,
                BigDecimal safety,
                BigDecimal minimum,
                BigDecimal multiple,
                BigDecimal maximumOrder,
                int nettingWeeks,
                int protectionWeeks) {
            this(
                    supplier,
                    purchased,
                    safety,
                    minimum,
                    multiple,
                    maximumOrder,
                    nettingWeeks,
                    protectionWeeks,
                    null,
                    null,
                    null);
        }
    }
}
