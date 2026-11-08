package cn.windwheel.aps.solve.calculate;

import cn.windwheel.aps.solve.model.SchedulingSolution;
import java.util.function.BiConsumer;

/** Only the notifications required by the shared forward/backward calendar calculation. */
public final class SchedulingScoreDirector {
    private final SchedulingSolution workingSolution;
    private final BiConsumer<Object, String> beforeChange;
    private final BiConsumer<Object, String> afterChange;

    public SchedulingScoreDirector(SchedulingSolution workingSolution,
                                   BiConsumer<Object, String> beforeChange,
                                   BiConsumer<Object, String> afterChange) {
        this.workingSolution = workingSolution;
        this.beforeChange = beforeChange;
        this.afterChange = afterChange;
    }

    public SchedulingSolution getWorkingSolution() {
        return workingSolution;
    }

    public void beforeVariableChanged(Object entity, String variable) {
        beforeChange.accept(entity, variable);
    }

    public void afterVariableChanged(Object entity, String variable) {
        afterChange.accept(entity, variable);
    }
}
