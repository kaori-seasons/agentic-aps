package cn.windwheel.aps.solve.calculate;

import cn.windwheel.aps.solve.model.SchedulingSolution;

public interface TaskTimeDirector {
    SchedulingSolution getWorkingSolution();
    void beforeVariableChanged(Object entity, String variable);
    void afterVariableChanged(Object entity, String variable);

    static TaskTimeDirector of(org.optaplanner.core.api.score.director.ScoreDirector<SchedulingSolution> director) {
        return new TaskTimeDirector() {
            public SchedulingSolution getWorkingSolution() { return director.getWorkingSolution(); }
            public void beforeVariableChanged(Object entity, String variable) { director.beforeVariableChanged(entity, variable); }
            public void afterVariableChanged(Object entity, String variable) { director.afterVariableChanged(entity, variable); }
        };
    }
}
