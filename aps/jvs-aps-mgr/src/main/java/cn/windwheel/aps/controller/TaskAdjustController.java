package cn.windwheel.aps.controller;

import cn.windwheel.aps.component.AdjustTaskFreezeHandler;
import cn.windwheel.aps.component.AdjustTaskMergeHandler;
import cn.windwheel.aps.component.AdjustTaskMoveHandler;
import cn.windwheel.aps.component.AdjustTaskSplitHandler;
import cn.windwheel.aps.dto.schedule.adjustment.*;
import cn.windwheel.aps.entity.PlanTaskAdjustPO;
import cn.windwheel.aps.enums.TaskFreezeTypeEnum;
import cn.windwheel.aps.enums.TaskMergeTypeEnum;
import cn.windwheel.aps.enums.TaskMoveTypeEnum;
import cn.windwheel.aps.enums.TaskSplitTypeEnum;
import cn.windwheel.aps.service.PlanTaskAdjustService;
import cn.windwheel.aps.service.ResourceGroupDataScopeService;
import cn.windwheel.aps.vo.schedule.TaskAdjustFreezeVO;
import cn.windwheel.aps.vo.schedule.TaskAdjustMergeVO;
import cn.windwheel.aps.vo.schedule.TaskAdjustMoveVO;
import cn.windwheel.aps.vo.schedule.TaskAdjustSplitVO;
import cn.windwheel.common.annotation.AuditOperation;
import cn.windwheel.common.annotation.RequirePermission;
import cn.windwheel.common.utils.R;
import cn.windwheel.common.utils.TenantContextHolder;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.AllArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * @author jvs
 */
@Api(tags = "[生产计划]排产任务调整")
@RestController
@RequestMapping("/task-adjust")
@AllArgsConstructor
@RequirePermission("aps:task-adjust:view")
public class TaskAdjustController {

    private final AdjustTaskSplitHandler splitHandler;
    private final AdjustTaskFreezeHandler freezeHandler;
    private final AdjustTaskMoveHandler moveHandler;
    private final AdjustTaskMergeHandler mergeHandler;
    private final PlanTaskAdjustService planTaskAdjustService;
    private final ResourceGroupDataScopeService resourceGroupDataScopeService;

    @ApiOperation("任务拆分——按拆出数量拆分")
    @PostMapping("/split/quantity")
    @RequirePermission("aps:task-adjust:edit")
    @AuditOperation("按数量拆分任务")
    public R<TaskAdjustSplitVO> splitByQuantity(@Validated @RequestBody SplitByQuantityDTO splitByQuantity) {
        resourceGroupDataScopeService.assertCanAccessTaskCodes(java.util.Collections.singleton(splitByQuantity.getTaskCode()));
        return R.ok(splitHandler.split(TaskSplitTypeEnum.SPLIT_BY_QUANTITY, splitByQuantity));
    }

    @ApiOperation("任务拆分——按任务数量平均拆分")
    @PostMapping("/split/evenly-task-number")
    @RequirePermission("aps:task-adjust:edit")
    @AuditOperation("平均拆分任务")
    public R<TaskAdjustSplitVO> splitEvenlyByTaskNumber(@Validated @RequestBody SplitEvenlyByTaskNumberDTO splitEvenlyByTaskNumber) {
        resourceGroupDataScopeService.assertCanAccessTaskCodes(java.util.Collections.singleton(splitEvenlyByTaskNumber.getTaskCode()));
        return R.ok(splitHandler.split(TaskSplitTypeEnum.SPLIT_EVENLY_BY_TASK_NUMBER, splitEvenlyByTaskNumber));
    }

    @ApiOperation("任务拆分——按完成数量拆分")
    @PostMapping("/split/completion")
    @RequirePermission("aps:task-adjust:edit")
    @AuditOperation("按完成数量拆分任务")
    public R<TaskAdjustSplitVO> splitByCompletion(@Validated @RequestBody SplitByCompletionDTO splitByCompletion) {
        resourceGroupDataScopeService.assertCanAccessTaskCodes(java.util.Collections.singleton(splitByCompletion.getTaskCode()));
        return R.ok(splitHandler.split(TaskSplitTypeEnum.SPLIT_BY_COMPLETION, splitByCompletion));
    }

    @ApiOperation("任务合并——选中的任务")
    @PostMapping("/merge/selected-tasks")
    @RequirePermission("aps:task-adjust:edit")
    @AuditOperation("合并选中任务")
    public R<TaskAdjustMergeVO> mergeSelectedTask(@Validated @RequestBody MergeSelectedTaskDTO mergeSelectedTask) {
        resourceGroupDataScopeService.assertCanAccessTaskCodes(mergeSelectedTask.getTaskCodes());
        return R.ok(mergeHandler.merge(TaskMergeTypeEnum.SELECTED_TASKS, mergeSelectedTask));
    }

    @ApiOperation("任务移动——移动指定任务")
    @PostMapping("/move/position")
    @RequirePermission("aps:task-adjust:edit")
    @AuditOperation("移动任务位置")
    public R<TaskAdjustMoveVO> movePosition(@Validated @RequestBody MovePositionDTO movePosition) {
        resourceGroupDataScopeService.assertCanAccessTaskCodes(java.util.Collections.singleton(movePosition.getTaskCode()));
        resourceGroupDataScopeService.assertCanAccessResourceId(movePosition.getResourceId());
        return R.ok(moveHandler.move(TaskMoveTypeEnum.MOVE_POSITION, movePosition));
    }

    @ApiOperation("任务移动——移动部分完成的任务进度到指定时间")
    @PostMapping("/move/partially-completed-task-time")
    @RequirePermission("aps:task-adjust:edit")
    @AuditOperation("移动部分完成任务进度")
    public R<TaskAdjustMoveVO> movePartiallyCompletedTaskTime(@Validated @RequestBody MovePartiallyCompletedTaskTimeDTO movePartiallyCompletedTaskTime) {
        return R.ok(moveHandler.move(TaskMoveTypeEnum.MOVE_PARTIALLY_COMPLETED_TASK_TIME, movePartiallyCompletedTaskTime));
    }

    @ApiOperation("锁定/解锁——选中的任务")
    @PostMapping("/freeze/selected-tasks")
    @RequirePermission("aps:task-adjust:edit")
    @AuditOperation("锁定或解锁选中任务")
    public R<TaskAdjustFreezeVO> freezeSelectedTask(@Validated @RequestBody FreezeSelectedTaskDTO selectedTask) {
        return R.ok(freezeHandler.freeze(TaskFreezeTypeEnum.SELECTED_TASKS, selectedTask));
    }

    @ApiOperation("锁定/解锁——所有已开工的任务")
    @PostMapping("/freeze/started-tasks")
    @RequirePermission("aps:task-adjust:edit")
    @AuditOperation("锁定或解锁已开工任务")
    public R<TaskAdjustFreezeVO> freezeStartedTask(@Validated @RequestBody FreezeStartedTaskDTO startedTask) {
        return R.ok(freezeHandler.freeze(TaskFreezeTypeEnum.STARTED_TASKS, startedTask));
    }

    @ApiOperation("撤销任务调整")
    @PostMapping("/cancel")
    @RequirePermission("aps:task-adjust:edit")
    @AuditOperation("撤销任务调整")
    public R<String> cancel() {
        java.util.List<String> taskIds = resourceGroupDataScopeService.filterPlanTaskAdjusts(planTaskAdjustService.list(Wrappers.<PlanTaskAdjustPO>lambdaQuery()
                        .eq(PlanTaskAdjustPO::getTenantId, TenantContextHolder.getTenantId())))
                .stream()
                .map(PlanTaskAdjustPO::getId)
                .toList();
        if (!taskIds.isEmpty()) {
            planTaskAdjustService.removeByIds(taskIds);
        }
        return R.ok();
    }

    @ApiOperation("检查任务是否已调整")
    @PostMapping("/adjusted")
    public R<Boolean> adjusted(){
        long count = resourceGroupDataScopeService.filterPlanTaskAdjusts(planTaskAdjustService.list(Wrappers.<PlanTaskAdjustPO>lambdaQuery()
                        .eq(PlanTaskAdjustPO::getTenantId, TenantContextHolder.getTenantId())))
                .size();
        return R.ok(count > 0);
    }
}
