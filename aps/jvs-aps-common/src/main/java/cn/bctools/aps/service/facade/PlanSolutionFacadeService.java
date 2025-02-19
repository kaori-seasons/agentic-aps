package cn.bctools.aps.service.facade;

import cn.bctools.aps.solve.model.BasicData;
import cn.bctools.aps.solve.model.SchedulingSolution;
import cn.bctools.aps.vo.schedule.report.PlanTaskGanttDetailVO;

/**
 * @author jvs
 * 智能排产聚合服务
 * <p>
 *     处理已生成的排产结果，不提供对排产任务的变更服务
 */
public interface PlanSolutionFacadeService {

    /**
     * 保存待确认排产计划
     *
     * @param basicData 排产基础数据
     * @param schedulingSolution 规划解决方案
     */
    void saveSolutionPending(BasicData basicData, SchedulingSolution schedulingSolution);

    /**
     * 删除当前租户待确认的求解任务
     */
    void removeSolutionPending();

    /**
     * 保存排产计划
     * <p>
     *     将待确认的排产计划正式保存为新的排产计划
     */
    void saveSolution();

    /**
     * 是否存在未确认的排产结果
     *
     * @return true-存在未确认的排产结果，false-不存在未确认的排产结果
     */
    boolean existsUnconfirmedTask();

    /**
     * 获取甘特图中的任务详情
     *
     * @param taskCode 任务编码
     * @return 甘特图任务详情
     */
    PlanTaskGanttDetailVO getPlanTaskGanttDetail(String taskCode);
}
