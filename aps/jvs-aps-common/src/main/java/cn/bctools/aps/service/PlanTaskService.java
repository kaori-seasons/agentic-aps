package cn.bctools.aps.service;

import cn.bctools.aps.entity.PlanTaskPO;
import com.baomidou.mybatisplus.extension.service.IService;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

/**
 * @author jvs
 * 排产计划任务
 */
public interface PlanTaskService extends IService<PlanTaskPO> {

    /**
     * 根据任务编码查询任务
     *
     * @param taskCode 任务编码
     * @return 任务
     */
    PlanTaskPO getByCode(String taskCode);

    /**
     * 根据任务编码集合查询任务及其所有子任务
     *
     * @param taskCodes 任务编码集合
     * @return 任务集合
     */
    List<PlanTaskPO> listSubTaskByCodes(Collection<String> taskCodes);

    /**
     * 根据任务编码集合查询任务
     *
     * @param taskCodes 任务编码集合
     * @return 任务集合
     */
    List<PlanTaskPO> listByCodes(Collection<String> taskCodes);

    /**
     * 批量查询合并任务的子任务
     *
     * @param mergeTaskCodes 合并任务编码集合
     * @return 合并任务的子任务集合
     */
    List<PlanTaskPO> listTaskByMergeTaskCodes(Collection<String> mergeTaskCodes);

    /**
     * 查询合并任务的子任务
     *
     * @param mergeTaskCode 合并任务编码
     * @return 合并任务的子任务集合
     */
    List<PlanTaskPO> listTaskByMergeTaskCode(String mergeTaskCode);

    /**
     * 查询未结束任务最早计划开始时间
     *
     * @return 任务最早计划开始时间
     */
    LocalDateTime getEarliestTaskStartTime();


}
