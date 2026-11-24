package cn.windwheel.aps.solve.adaptive;

import cn.windwheel.aps.entity.dto.planning.AdaptiveSolveConfig;
import cn.windwheel.aps.solve.model.SchedulingSolution;
import cn.windwheel.aps.solve.score.SchedulingRules;

/** Deterministic size tiers; elapsed wall time can only reduce a trial's allowance. */
public final class AdaptiveBudgetPolicy {
    private AdaptiveBudgetPolicy() {}

    public record Plan(String mode, int activeTasks, int movableTasks, int resourceCount,
                       String tier, int plannedTrials, long coldMillis, long warmMillis,
                       long coldScoreLimit, long warmScoreLimit) {
        public Allocation allocate(boolean warm, long remainingMillis, int remainingTrials) {
            long cap = warm ? warmMillis : coldMillis;
            // Reserve a fair slice for the remaining candidates once a feasible incumbent exists.
            if (warm && "SCALE_AWARE".equals(mode)) {
                cap = Math.min(cap, Math.max(1, remainingMillis / Math.max(1, remainingTrials)));
            }
            return new Allocation(Math.max(0, Math.min(cap, remainingMillis)),
                    warm ? warmScoreLimit : coldScoreLimit);
        }
    }
    public record Allocation(long millis, long scoreLimit) {}

    public static Plan plan(SchedulingSolution problem, AdaptiveSolveConfig config) {
        config.validate();
        int active = (int) problem.getTasks().stream().filter(SchedulingRules::active).count();
        int movable = (int) problem.getTasks().stream().filter(SchedulingRules::active)
                .filter(t -> !Boolean.TRUE.equals(t.getPinned())).count();
        int trials = config.getPopulationSize() * (config.getGenerations() + 1);
        // Frozen tasks still contribute to scoring cost, even though they are not searched.
        int size = Math.max(movable, (active + 1) / 2);
        String tier = size <= 48 ? "SMALL" : size <= 120 ? "MEDIUM" : "LARGE";
        long baseMillis = config.getTrialSeconds() * 1000;
        long coldMillis = baseMillis;
        long coldScores = config.getTrialScoreLimit();
        if (config.getBudgetMode() == AdaptiveSolveConfig.BudgetMode.SCALE_AWARE) {
            int share = size <= 48 ? 25 : size <= 120 ? 40 : 60;
            int multiplier = size <= 48 ? 1 : size <= 120 ? 3 : 6;
            coldMillis = Math.min(config.getTotalSeconds() * 1000,
                    Math.max(baseMillis, config.getTotalSeconds() * 1000 * share / 100));
            coldScores = Math.min(1_000_000L, coldScores * multiplier);
        }
        return new Plan(config.getBudgetMode().name(), active, movable, problem.getMainResources().size(),
                tier, trials, coldMillis, baseMillis, coldScores, config.getTrialScoreLimit());
    }
}
