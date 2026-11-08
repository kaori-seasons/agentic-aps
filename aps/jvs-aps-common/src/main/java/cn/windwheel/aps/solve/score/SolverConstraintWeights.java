package cn.windwheel.aps.solve.score;

import lombok.Getter;
import lombok.Setter;

/** Set once by the engine's constraintProviderCustomProperties, never via ThreadLocal.
 * Each SolverFactory owns its own provider, including on SolverManager worker threads.
 */
@Getter
@Setter
public abstract class SolverConstraintWeights {
    private long priorityWeight = 50;
    private long finishEarlyWeight = 1;
    private long minimizeTardinessWeight = 1;
    private long balanceResourceLoadWeight = 1;
    private long minimizeGapBetweenDependentTasksWeight = 1;
    private long minimizeSetupTimeWeight = 0;
}
