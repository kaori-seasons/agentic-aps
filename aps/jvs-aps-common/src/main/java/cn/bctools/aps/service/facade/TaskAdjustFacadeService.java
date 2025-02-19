package cn.bctools.aps.service.facade;

import cn.bctools.aps.dto.schedule.TaskDTO;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

/**
 * @author jvs
 * 排产任务调整聚合服务
 */
public interface TaskAdjustFacadeService {

    /**
     * 保存调整任务
     *
     * @param taskAdjustList 任务集合
     */
    void saveAdjustTasks(List<TaskDTO> taskAdjustList);

    /**
     * 查询所有未结束订单的任务
     * <p>
     * 不包括订单任务已全部结束的任务
     *
     * @return 任务集合
     */
    List<TaskDTO> listAllTasksWithAdjusts();

    /**
     * 查询所有部分完成的任务
     *
     * <p>
     * 分别从已调整任务表、任务表中查询部分完成的任务；
     * 合并已调整任务集合和任务集合，同一个任务在两个集合中都存在时，以已调整任务集合中的任务优先
     *
     * @return 状态为“部分完成”的任务集合
     */
    List<TaskDTO> listPartiallyCompletedTask();

    /**
     * 查询指定任务
     *
     * <p>
     * 优先从已调整任务表中查询，若没有，则从任务表中查询
     *
     * @param taskCode 待调整的任务编码
     * @return 任务
     */
    TaskDTO getTaskByCode(String taskCode);

    /**
     * 查询多个任务
     *
     * <p>
     * 分别从已调整任务表、任务表中根据任务编码查询任务；
     * 合并已调整任务集合和任务集合，同一个任务在两个集合中都存在时，以已调整任务集合中的任务优先
     *
     * @param taskCodes 任务编码集合
     * @return 任务集合
     */
    List<TaskDTO> listTaskByCodes(Collection<String> taskCodes);

    /**
     * 查询指定资源下，计划开始时间在指定时间之后的所有任务
     *
     * <p>
     * 分别从已调整任务表、任务表中根据资源id和时间点查询任务；
     * 合并已调整任务集合和任务集合，同一个任务在两个集合中都存在时，以已调整任务集合中的任务优先
     *
     * @param resourceId 资源id
     * @param time       时间点（查询计划开始时间在此时间点之后的所有任务）
     * @return 任务集合
     */
    List<TaskDTO> listTasksWithAdjustsByResourceId(String resourceId, LocalDateTime time);

    /**
     * 批量查询指定资源未完成的任务
     *
     * <p>
     * 分别从已调整任务表、任务表中查询未完成的任务
     * 合并已调整任务集合和任务集合，同一个任务在两个集合中都存在时，以已调整任务集合中的任务优先
     *
     * @param resourceIds 资源id集合
     * @return 任务集合
     */
    List<TaskDTO> listTasksWithAdjustsByResourceIds(Collection<String> resourceIds);


    /**
     * 查询单个合并任务的子任务
     *
     * <p>
     * 分别从已调整任务表、任务表中根据合并任务编码查询任务；
     * 合并已调整任务集合和任务集合，同一个任务在两个集合中都存在时，以已调整任务集合中的任务优先
     *
     * @param mergeTaskCode 合并任务编码
     * @return 合并任务的子任务集合
     */
    List<TaskDTO> listTaskByMergeTaskCode(String mergeTaskCode);


    /**
     * 查询多个合并任务的子任务
     *
     * <p>
     * 分别从已调整任务表、任务表中根据合并任务编码集合查询任务；
     * 合并已调整任务集合和任务集合，同一个任务在两个集合中都存在时，以已调整任务集合中的任务优先
     *
     * @param mergeTaskCodes 合并任务编码集合
     * @return 合并任务的子任务集合
     */
    List<TaskDTO> listTaskByMergeTaskCodes(Collection<String> mergeTaskCodes);

    /**
     * 查询日期范围内的任务
     *
     * @param beginDate 开始日期
     * @param endDate   结束日期
     * @return 日期范围内的任务
     */
    List<TaskDTO> listTasksByDateRange(LocalDateTime beginDate, LocalDateTime endDate);

    /**
     * 查询未结束任务最早计划开始时间
     *
     * @return 任务最早计划开始时间
     */
    LocalDateTime getEarliestTaskStartTime();
}
