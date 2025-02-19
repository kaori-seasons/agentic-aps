package cn.bctools.aps.solve.calculate.service;

import cn.bctools.aps.solve.model.MainProductionResource;
import cn.bctools.aps.solve.model.ProductionTask;
import cn.bctools.aps.solve.model.SchedulingSolution;
import cn.bctools.aps.solve.model.WorkCalendar;
import org.optaplanner.core.api.score.director.ScoreDirector;

import java.time.LocalDateTime;
import java.util.List;

/**
 * @author jvs
 * 计算任务时间
 */
public interface TaskTimeService {

    /**
     * 修改任务开始时间
     *
     * @param scoreDirector
     * @param resource 主资源
     * @param fromIndex 变更任务所在目标资源的下标
     */
    void updateTaskStartTime(ScoreDirector<SchedulingSolution> scoreDirector, MainProductionResource resource, int fromIndex);

    /**
     * 计算任务开始时间
     *
     * @param task                    任务
     * @param resourcePreviousTask    当前任务所在资源的前一个任务
     * @param resourcePreviousEndTime 当前任务所在资源的前一个任务的结束时间
     * @param tasks                   任务集合
     * @param workCalendars           任务所在资源可用日历集合
     * @return 任务开始时间
     */
    LocalDateTime calculateStartTime(ProductionTask task, ProductionTask resourcePreviousTask, LocalDateTime resourcePreviousEndTime, List<ProductionTask> tasks, List<WorkCalendar> workCalendars, List<ProductionTask> currentResourcePreviousTasks );
}
