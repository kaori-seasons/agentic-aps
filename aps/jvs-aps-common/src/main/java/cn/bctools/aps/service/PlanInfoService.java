package cn.bctools.aps.service;

import cn.bctools.aps.entity.PlanInfoPO;
import cn.bctools.aps.solve.model.PlanningStrategy;
import com.baomidou.mybatisplus.extension.service.IService;

import java.time.LocalDateTime;

/**
 * @author jvs
 * 排程计划基本信息
 */
public interface PlanInfoService extends IService<PlanInfoPO> {

    /**
     * 创建待确认的排程计划
     *
     * @param strategy 排产策略
     */
    void createUnconfirmedPlan(PlanningStrategy strategy);

    /**
     * 确认排程计划
     *
     * @param earliestTaskStartTime 未结束任务最早计划开始时间
     */
    void confirmedPlan(LocalDateTime earliestTaskStartTime);

    /**
     * 删除待确认排程计划
     */
    void removeUnconfirmedPlan();


    /**
     * 修改排程计划最近派工截止时间
     *
     * @param lastTaskAssignmentTime 最近任务派工截止时间
     */
    void updateAssignmentTime(LocalDateTime lastTaskAssignmentTime);

    /**
     * 获取已确认排程计划
     *
     * @return 排程计划
     */
    PlanInfoPO getConfirmedPlan();
}
