package cn.bctools.aps.service.facade;

import cn.bctools.aps.dto.GeneratePlanningSmartDTO;
import cn.bctools.aps.vo.schedule.PlanProgressVO;

/**
 * @author jvs
 * 智能排产
 */
public interface PlanningSmartService {

    /**
     * 生成排产计划
     *
     * @param generatePlanningSmart 排产参数
     */
    void generate(GeneratePlanningSmartDTO generatePlanningSmart);

    /**
     * 获取排产进度
     *
     * @return 排产进度
     */
    PlanProgressVO getPlanProgress();

    /**
     * 放弃计划
     * <p>
     *     放弃待确认排产计划
     */
    void cancelPlanPending();

    /**
     * 确认计划
     * <p>
     *     将待确认计划转储为正式计划
     */
    void confirmPlan();
}
