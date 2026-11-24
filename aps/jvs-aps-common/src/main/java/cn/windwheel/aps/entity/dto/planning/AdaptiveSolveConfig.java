package cn.windwheel.aps.entity.dto.planning;

import lombok.Data;
import lombok.experimental.Accessors;

/** Optional, bounded per-run parameter evolution. No automatic policy promotion. */
@Data
@Accessors(chain = true)
public class AdaptiveSolveConfig {
    public enum BudgetMode { FIXED, SCALE_AWARE }
    private boolean enabled;
    /** Only reuse feasible incumbents from this solve; never import a historical plan implicitly. */
    private boolean warmStartEnabled = true;
    private BudgetMode budgetMode = BudgetMode.SCALE_AWARE;
    private int populationSize = 6;
    private int generations = 2;
    private long totalSeconds = 180;
    private long trialSeconds = 15;
    private long trialScoreLimit = 5000;

    public void validate() {
        if (budgetMode == null || populationSize < 4 || populationSize > 16 || generations < 0 || generations > 10
                || totalSeconds < 1 || totalSeconds > 3600 || trialSeconds < 1
                || trialSeconds > totalSeconds || trialScoreLimit < 10 || trialScoreLimit > 1000000) {
            throw new IllegalArgumentException("自适应求解种群、代数或预算超出范围");
        }
    }
}
