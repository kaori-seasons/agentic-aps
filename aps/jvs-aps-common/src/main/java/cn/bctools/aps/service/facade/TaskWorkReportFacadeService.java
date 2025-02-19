package cn.bctools.aps.service.facade;

import cn.bctools.aps.dto.schedule.UpdateWorkReportProgress;
import cn.bctools.aps.dto.schedule.WorkReportDTO;

import java.util.List;

/**
 * @author jvs
 * 任务报工聚合服务
 */
public interface TaskWorkReportFacadeService {

    /**
     * 批量读入报工信息并更新任务进度
     *
     * @param workReportList 报工信息
     */
    void importBatchWorkReportRefreshTaskProgress(List<WorkReportDTO> workReportList);

    /**
     * 修改指定任务的报工进度
     *
     * @param updateWorkReportProgress 报工信息
     */
    void updateTaskProgress(UpdateWorkReportProgress updateWorkReportProgress);
}
