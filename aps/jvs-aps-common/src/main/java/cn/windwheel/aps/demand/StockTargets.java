package cn.windwheel.aps.demand;

import static cn.windwheel.aps.demand.PlanningChecks.*;

import static java.math.BigDecimal.ZERO;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.ArrayList;

/** Empirical protection-period forecast error quantile and optional economic lot. */
public record StockTargets(String method, int samples, BigDecimal safety, BigDecimal economicLot) {
    static StockTargets derive(PlanningInput input) {
        var s = input.samples();
        BigDecimal safety = input.policy().safety(), lot = ZERO;
        if (s == null) return new StockTargets("FIXED", 0, safety, lot);
        require(
                s.serviceLevel() != null
                        && s.serviceLevel().signum() > 0
                        && s.serviceLevel().compareTo(BigDecimal.ONE) < 0,
                "周期服务水平须介于0与1之间");
        require(s.observations() != null && s.observations().size() <= 2000, "保护期观察样本缺失或超限");
        var errors = new ArrayList<BigDecimal>();
        for (var obs : s.observations()) {
            require(
                    obs != null && obs.knownAt() != null && !obs.knownAt().isAfter(input.asOf()),
                    "样本含未来信息");
            quantity(obs.actual());
            quantity(obs.forecast());
            errors.add(obs.actual().subtract(obs.forecast()));
        }
        String method = "FIXED_LOW_SAMPLE";
        // Need at least one expected observation in the upper tail, and twelve periods overall.
        if (errors.size() >= 12
                && BigDecimal.ONE
                                .subtract(s.serviceLevel())
                                .multiply(BigDecimal.valueOf(errors.size()))
                                .compareTo(BigDecimal.ONE)
                        >= 0) {
            errors.sort(BigDecimal::compareTo);
            int rank =
                    s.serviceLevel()
                            .multiply(BigDecimal.valueOf(errors.size()))
                            .setScale(0, RoundingMode.CEILING)
                            .intValueExact();
            safety = errors.get(rank - 1).max(ZERO);
            method = "PROTECTION_ERROR_QUANTILE";
        }
        if (s.annualDemand() != null
                || s.orderCost() != null
                || s.annualHoldingCostPerUnit() != null) {
            quantity(s.annualDemand());
            quantity(s.orderCost());
            quantity(s.annualHoldingCostPerUnit());
            require(s.annualHoldingCostPerUnit().signum() > 0, "单位年持有成本须为正数");
            lot =
                    s.annualDemand()
                            .multiply(s.orderCost())
                            .multiply(BigDecimal.valueOf(2))
                            .divide(s.annualHoldingCostPerUnit(), MathContext.DECIMAL128)
                            .sqrt(MathContext.DECIMAL128)
                            .setScale(6, RoundingMode.HALF_UP);
        }
        return new StockTargets(method, errors.size(), safety, lot);
    }
}
