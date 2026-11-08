package cn.windwheel.aps.solve.calculate;

import ai.timefold.solver.core.api.domain.variable.ListVariableListener;
import ai.timefold.solver.core.api.score.director.ScoreDirector;
import cn.windwheel.aps.component.PlanningDirectionHandler;
import cn.windwheel.aps.solve.model.MainProductionResource;
import cn.windwheel.aps.solve.model.ProductionTask;
import cn.windwheel.aps.solve.model.SchedulingSolution;
import cn.windwheel.common.utils.SpringContextUtil;

/** Timefold adapter; both engines share exactly the same calendar and dependency propagation. */
public class TimefoldStartTimeListener
        implements ListVariableListener<SchedulingSolution, MainProductionResource, ProductionTask> {
    private PlanningDirectionHandler directionHandler;

    @Override
    public void afterListVariableElementUnassigned(ScoreDirector<SchedulingSolution> director, ProductionTask task) {
        director.beforeVariableChanged(task, "startTime");
        task.setStartTime(null);
        director.afterVariableChanged(task, "startTime");
    }

    @Override
    public void beforeListVariableChanged(ScoreDirector<SchedulingSolution> director,
                                          MainProductionResource resource, int fromIndex, int toIndex) { }

    @Override
    public void afterListVariableChanged(ScoreDirector<SchedulingSolution> director,
                                         MainProductionResource resource, int fromIndex, int toIndex) {
        if (directionHandler == null) {
            directionHandler = SpringContextUtil.getBean(PlanningDirectionHandler.class);
        }
        directionHandler.getTaskTimeService(director.getWorkingSolution().getStrategy().getConfig().getDirection())
                .updateTaskStartTime(new SchedulingScoreDirector(director.getWorkingSolution(),
                        director::beforeVariableChanged, director::afterVariableChanged), resource, fromIndex);
    }

    @Override
    public void beforeEntityAdded(ScoreDirector<SchedulingSolution> director, MainProductionResource resource) { }
    @Override
    public void afterEntityAdded(ScoreDirector<SchedulingSolution> director, MainProductionResource resource) { }
    @Override
    public void beforeEntityRemoved(ScoreDirector<SchedulingSolution> director, MainProductionResource resource) { }
    @Override
    public void afterEntityRemoved(ScoreDirector<SchedulingSolution> director, MainProductionResource resource) { }
}
