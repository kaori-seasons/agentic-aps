package cn.windwheel.aps.controller;

import cn.windwheel.aps.dto.schedule.UpdateWorkReportProgress;
import cn.windwheel.aps.dto.schedule.WorkReportDTO;
import cn.windwheel.aps.service.facade.TaskWorkReportFacadeService;
import cn.windwheel.aps.vo.schedule.WorkAssignmentExcelVO;
import cn.windwheel.common.annotation.AuditOperation;
import cn.windwheel.common.annotation.RequirePermission;
import cn.windwheel.common.exception.BusinessException;
import cn.windwheel.common.utils.BeanCopyUtil;
import cn.windwheel.common.utils.ObjectNull;
import cn.windwheel.common.utils.R;
import com.alibaba.excel.EasyExcelFactory;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

/**
 * @author jvs
 */
@Slf4j
@Api(tags = "报工")
@RestController
@RequestMapping("/task-report")
@AllArgsConstructor
@RequirePermission("aps:task-report:edit")
public class TaskReportController {

    private final TaskWorkReportFacadeService taskWorkReportFacadeService;

    @ApiOperation(value = "导入")
    @PostMapping("/import")
    @RequirePermission("aps:task-report:import")
    @AuditOperation("导入报工记录")
    public R<String> treeImport(@RequestParam("file") MultipartFile file) {
        List<WorkReportDTO> workReportList = null;
        try {
            List<WorkAssignmentExcelVO> excelDataList = EasyExcelFactory.read(file.getInputStream())
                    .head(WorkAssignmentExcelVO.class)
                    .doReadAllSync();
            workReportList = BeanCopyUtil.copys(excelDataList, WorkReportDTO.class);
        } catch (IOException e) {
            log.error("读取报工文件失败：", e);
            throw new BusinessException("读取报工文件失败");
        }
        taskWorkReportFacadeService.importBatchWorkReportRefreshTaskProgress(workReportList);
        return R.ok();
    }


    @ApiOperation(value = "修改指定任务报工进度")
    @PostMapping("/update/progress")
    @AuditOperation("修改任务报工进度")
    public R<String> updateProgress(@Validated @RequestBody UpdateWorkReportProgress updateWorkReportProgress) {
        taskWorkReportFacadeService.updateTaskProgress(updateWorkReportProgress);
        return R.ok();
    }

    @ApiOperation(value = "批量修改指定任务报工进度")
    @PostMapping("/update/batch/progress")
    @AuditOperation("批量修改任务报工进度")
    public R<String> updateProgress(@Validated @RequestBody List<UpdateWorkReportProgress> updateWorkReportProgress) {
        if (ObjectNull.isNull(updateWorkReportProgress)) {
            return R.ok();
        }
        updateWorkReportProgress.forEach(taskWorkReportFacadeService::updateTaskProgress);
        return R.ok();
    }
}
