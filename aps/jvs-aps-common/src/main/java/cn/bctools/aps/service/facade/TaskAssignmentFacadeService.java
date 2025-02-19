package cn.bctools.aps.service.facade;

import cn.bctools.aps.dto.schedule.WorkAssignmentDTO;
import cn.bctools.aps.vo.schedule.WorkAssignmentVO;

import java.util.List;

/**
 * @author jvs
 * 派工聚合服务
 */
public interface TaskAssignmentFacadeService {

    /**
     * 派工
     * <p>
     * 输出派工参考任务安排
     *
     * @param workAssignment 入参
     * @return 任务计划
     */
    List<WorkAssignmentVO> workAssign(WorkAssignmentDTO workAssignment);
}
