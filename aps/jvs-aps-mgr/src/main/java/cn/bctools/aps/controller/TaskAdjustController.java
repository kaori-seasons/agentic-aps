package cn.bctools.aps.controller;

import cn.bctools.aps.component.AdjustTaskFreezeHandler;
import cn.bctools.aps.component.AdjustTaskMergeHandler;
import cn.bctools.aps.component.AdjustTaskMoveHandler;
import cn.bctools.aps.component.AdjustTaskSplitHandler;
import cn.bctools.aps.dto.schedule.adjustment.*;
import cn.bctools.aps.entity.PlanTaskAdjustPO;
import cn.bctools.aps.enums.TaskFreezeTypeEnum;
import cn.bctools.aps.enums.TaskMergeTypeEnum;
import cn.bctools.aps.enums.TaskMoveTypeEnum;
import cn.bctools.aps.enums.TaskSplitTypeEnum;
import cn.bctools.aps.service.PlanTaskAdjustService;
import cn.bctools.aps.vo.schedule.TaskAdjustFreezeVO;
import cn.bctools.aps.vo.schedule.TaskAdjustMergeVO;
import cn.bctools.aps.vo.schedule.TaskAdjustMoveVO;
import cn.bctools.aps.vo.schedule.TaskAdjustSplitVO;
import cn.bctools.common.utils.R;
import cn.bctools.common.utils.TenantContextHolder;
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
public class TaskAdjustController {

    private final AdjustTaskSplitHandler splitHandler;
    private final AdjustTaskFreezeHandler freezeHandler;
    private final AdjustTaskMoveHandler moveHandler;
    private final AdjustTaskMergeHandler mergeHandler;
    private final PlanTaskAdjustService planTaskAdjustService;

    @ApiOperation("任务拆分——按拆出数量拆分")
    @PostMapping("/split/quantity")
    public R<TaskAdjustSplitVO> splitByQuantity(@Validated @RequestBody SplitByQuantityDTO splitByQuantity) {
        return R.ok(splitHandler.split(TaskSplitTypeEnum.SPLIT_BY_QUANTITY, splitByQuantity));
    }

    @ApiOperation("任务拆分——按任务数量平均拆分")
    @PostMapping("/split/evenly-task-number")
    public R<TaskAdjustSplitVO> splitEvenlyByTaskNumber(@Validated @RequestBody SplitEvenlyByTaskNumberDTO splitEvenlyByTaskNumber) {
        return R.ok(splitHandler.split(TaskSplitTypeEnum.SPLIT_EVENLY_BY_TASK_NUMBER, splitEvenlyByTaskNumber));
    }

    @ApiOperation("任务拆分——按完成数量拆分")
    @PostMapping("/split/completion")
    public R<TaskAdjustSplitVO> splitByCompletion(@Validated @RequestBody SplitByCompletionDTO splitByCompletion) {
        return R.ok(splitHandler.split(TaskSplitTypeEnum.SPLIT_BY_COMPLETION, splitByCompletion));
    }

    @ApiOperation("任务合并——选中的任务")
    @PostMapping("/merge/selected-tasks")
    public R<TaskAdjustMergeVO> mergeSelectedTask(@Validated @RequestBody MergeSelectedTaskDTO mergeSelectedTask) {
        return R.ok(mergeHandler.merge(TaskMergeTypeEnum.SELECTED_TASKS, mergeSelectedTask));
    }

    @ApiOperation("任务移动——移动指定任务")
    @PostMapping("/move/position")
    public R<TaskAdjustMoveVO> movePosition(@Validated @RequestBody MovePositionDTO movePosition) {
        return R.ok(moveHandler.move(TaskMoveTypeEnum.MOVE_POSITION, movePosition));
    }

    @ApiOperation("任务移动——移动部分完成的任务进度到指定时间")
    @PostMapping("/move/partially-completed-task-time")
    public R<TaskAdjustMoveVO> movePartiallyCompletedTaskTime(@Validated @RequestBody MovePartiallyCompletedTaskTimeDTO movePartiallyCompletedTaskTime) {
        return R.ok(moveHandler.move(TaskMoveTypeEnum.MOVE_PARTIALLY_COMPLETED_TASK_TIME, movePartiallyCompletedTaskTime));
    }

    @ApiOperation("锁定/解锁——选中的任务")
    @PostMapping("/freeze/selected-tasks")
    public R<TaskAdjustFreezeVO> freezeSelectedTask(@Validated @RequestBody FreezeSelectedTaskDTO selectedTask) {
        return R.ok(freezeHandler.freeze(TaskFreezeTypeEnum.SELECTED_TASKS, selectedTask));
    }

    @ApiOperation("锁定/解锁——所有已开工的任务")
    @PostMapping("/freeze/started-tasks")
    public R<TaskAdjustFreezeVO> freezeStartedTask(@Validated @RequestBody FreezeStartedTaskDTO startedTask) {
        return R.ok(freezeHandler.freeze(TaskFreezeTypeEnum.STARTED_TASKS, startedTask));
    }

    @ApiOperation("撤销任务调整")
    @PostMapping("/cancel")
    public R<String> cancel() {
        planTaskAdjustService.remove(Wrappers.<PlanTaskAdjustPO>lambdaQuery()
                .eq(PlanTaskAdjustPO::getTenantId, TenantContextHolder.getTenantId()));
        return R.ok();
    }

    @ApiOperation("检查任务是否已调整")
    @PostMapping("/adjusted")
    public R<Boolean> adjusted(){
        long count = planTaskAdjustService.count(Wrappers.<PlanTaskAdjustPO>lambdaQuery()
                .eq(PlanTaskAdjustPO::getTenantId, TenantContextHolder.getTenantId()));
        return R.ok(count > 0);
    }
}
