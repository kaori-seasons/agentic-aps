package cn.bctools.aps.solve.calculate;

import cn.bctools.aps.component.PlanningDirectionHandler;
import cn.bctools.aps.entity.enums.PlanningDirectionEnum;
import cn.bctools.aps.solve.calculate.service.TaskTimeService;
import cn.bctools.aps.solve.model.MainProductionResource;
import cn.bctools.aps.solve.model.ProductionTask;
import cn.bctools.aps.solve.model.SchedulingSolution;
import cn.bctools.common.utils.SpringContextUtil;
import lombok.extern.slf4j.Slf4j;
import org.optaplanner.core.api.domain.variable.ListVariableListener;
import org.optaplanner.core.api.score.director.ScoreDirector;

/**
 * @author jvs
 * 任务分配主资源变更时，更新阴影变量
 */
@Slf4j
public class ApsStartTimeListener implements ListVariableListener<SchedulingSolution, MainProductionResource, ProductionTask> {

    private final PlanningDirectionHandler planningDirectionHandler = SpringContextUtil.getBean(PlanningDirectionHandler.class);

    @Override
    public void afterListVariableElementUnassigned(ScoreDirector<SchedulingSolution> scoreDirector, ProductionTask productionTask) {
        scoreDirector.beforeVariableChanged(productionTask, "startTime");
        productionTask.setStartTime(null);
        scoreDirector.afterVariableChanged(productionTask, "startTime");
    }

    @Override
    public void beforeListVariableChanged(ScoreDirector<SchedulingSolution> scoreDirector, MainProductionResource resource, int fromIndex, int toIndex) {
        // 无需重写
    }

    @Override
    public void afterListVariableChanged(ScoreDirector<SchedulingSolution> scoreDirector, MainProductionResource resource, int fromIndex, int toIndex) {
        // 根据排产方向计算并修改任务开始时间
        PlanningDirectionEnum direction = scoreDirector.getWorkingSolution().getStrategy().getConfig().getDirection();

        // 修改开始时间
        TaskTimeService taskTimeService = planningDirectionHandler.getTaskTimeService(direction);
        taskTimeService.updateTaskStartTime(scoreDirector, resource, fromIndex);
    }

    @Override
    public void beforeEntityAdded(ScoreDirector<SchedulingSolution> scoreDirector, MainProductionResource productionResource) {
        // 无需重写
    }

    @Override
    public void afterEntityAdded(ScoreDirector<SchedulingSolution> scoreDirector, MainProductionResource productionResource) {
        // 无需重写
    }

    @Override
    public void beforeEntityRemoved(ScoreDirector<SchedulingSolution> scoreDirector, MainProductionResource productionResource) {
        // 无需重写
    }

    @Override
    public void afterEntityRemoved(ScoreDirector<SchedulingSolution> scoreDirector, MainProductionResource productionResource) {
        // 无需重写
    }
}
