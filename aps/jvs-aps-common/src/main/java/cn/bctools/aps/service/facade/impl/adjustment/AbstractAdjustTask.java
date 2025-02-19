package cn.bctools.aps.service.facade.impl.adjustment;

import cn.bctools.aps.component.PlanningDirectionHandler;
import cn.bctools.aps.component.ReportHandler;
import cn.bctools.aps.dto.schedule.TaskDTO;
import cn.bctools.aps.entity.PlanTaskOrderPO;
import cn.bctools.aps.entity.ProductionResourcePO;
import cn.bctools.aps.entity.enums.PlanningDirectionEnum;
import cn.bctools.aps.entity.enums.ReportTypeEnum;
import cn.bctools.aps.service.PlanTaskOrderService;
import cn.bctools.aps.service.ProductionResourceService;
import cn.bctools.aps.service.facade.TaskAdjustFacadeService;
import cn.bctools.aps.service.facade.WorkCalendarFacadeService;
import cn.bctools.aps.solve.calculate.service.TaskTimeService;
import cn.bctools.aps.solve.model.MainProductionResource;
import cn.bctools.aps.solve.model.ProductionTask;
import cn.bctools.aps.solve.model.WorkCalendar;
import cn.bctools.aps.solve.util.TaskCalendarUtils;
import cn.bctools.aps.solve.util.TaskDurationUtils;
import cn.bctools.aps.solve.util.TaskValidatorUtils;
import cn.bctools.aps.util.PlanUtils;
import cn.bctools.aps.vo.DetailReportFieldSettingVO;
import cn.bctools.aps.vo.schedule.report.ResourceGanttTaskVO;
import cn.bctools.common.utils.BeanCopyUtil;
import cn.bctools.common.utils.ObjectNull;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;

import javax.annotation.Resource;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * @author jvs
 * 任务调整顶级抽象类
 */
public abstract class AbstractAdjustTask {

    /**
     * 拆分任务编码前缀
     */
    public static final String SPLIT_TASK_CODE_PREFIX = "S-";

    /**
     * 合并任务编码前缀
     */
    public static final String MERGE_TASK_CODE_PREFIX = "M-";

    @Resource
    protected TaskAdjustFacadeService taskAdjustFacadeService;
    @Resource
    protected ProductionResourceService productionResourceService;
    @Resource
    protected PlanTaskOrderService planTaskOrderService;
    @Resource
    protected WorkCalendarFacadeService workCalendarFacadeService;
    @Resource
    private ReportHandler reportHandler;
    @Resource
    private PlanningDirectionHandler planningDirectionHandler;

    /**
     * 检查是否有任务不合规
     * <p>
     * 将不合规的任务加入已调整任务集合
     *
     * @param taskAdjustList 已调整任务
     */
    public void checkTaskCompliance(List<TaskDTO> taskAdjustList) {
        List<TaskDTO> allPlanTasks = taskAdjustFacadeService.listAllTasksWithAdjusts();
        // 先从查询结果中移除本次调整任务集合包含的任务，再将本次调整的任务加入集合
        List<String> taskAdjustCodeList = taskAdjustList.stream()
                .map(TaskDTO::getCode)
                .toList();
        allPlanTasks.removeIf(task -> taskAdjustCodeList.contains(task.getCode()));
        allPlanTasks.addAll(taskAdjustList);

        // 校验任务是否合规，返回不合规的任务编码集合
        Set<String> nonCompliantTaskCodes = TaskValidatorUtils.checkTaskCompliance(allPlanTasks);

        List<TaskDTO> nonCompliantTaskAdjusts = new ArrayList<>();
        nonCompliantTaskCodes.forEach(nonCompliantTaskCode -> {
            // 根据不合规任务编码，筛选任务
            Optional<TaskDTO> optionalNonCompliantTask = allPlanTasks.stream()
                    .filter(task -> task.getCode().equals(nonCompliantTaskCode))
                    .findFirst();
            if (optionalNonCompliantTask.isPresent()) {
                TaskDTO nonCompliantTask = optionalNonCompliantTask.get();
                Optional<TaskDTO> optionalTaskAdjust = taskAdjustList.stream()
                        .filter(task -> task.getCode().equals(nonCompliantTask.getCode()))
                        .findFirst();
                if (optionalTaskAdjust.isPresent()) {
                    optionalTaskAdjust.get().setCompliant(false);
                } else {
                    nonCompliantTaskAdjusts.add(
                            BeanCopyUtil.copy(nonCompliantTask, TaskDTO.class)
                                    .setDiscard(false)
                                    .setCompliant(false)
                    );
                }
            }
        });
        taskAdjustList.addAll(nonCompliantTaskAdjusts);
    }

    /**
     * 获取任务的所有前置任务
     *
     * @param taskAdjustList 任务集合
     * @return 任务的所有前置任务
     */
    protected List<TaskDTO> listAdjustTaskFrontTasks(List<TaskDTO> taskAdjustList) {
        Set<String> frontCodes = taskAdjustList.stream()
                .filter(task -> ObjectNull.isNotNull(task.getFrontTaskCodes()))
                .flatMap(task -> task.getFrontTaskCodes().stream())
                .collect(Collectors.toSet());
        if (ObjectNull.isNull(frontCodes)) {
            return Collections.emptyList();
        }
        return taskAdjustFacadeService.listTaskByCodes(frontCodes)
                .stream()
                .filter(task -> ObjectNull.isNull(task.getMergeTaskCode()))
                .collect(Collectors.toList());
    }

    /**
     * 计算任务时间
     * <p>
     * 以当前任务所在资源的前一个任务的结束时间作为当前任务最早开始时间（后续可能添加其它配置，如切换工单时长）
     *
     * @param taskAdjustList   移动后影响的目标资源任务(按计划开始时间顺序排序)
     * @param frontTasks       移动后影响的目标资源任务的前置任务
     * @param workCalendarMap 可直接作用于排产计算的工作日历.Map<资源id, 工作日历集合>
     */
    protected void calculateTaskTime(List<TaskDTO> taskAdjustList, List<TaskDTO> frontTasks, Map<String, List<WorkCalendar>> workCalendarMap) {
        List<ProductionTask> productionTasks = Optional.ofNullable(frontTasks).orElseGet(ArrayList::new)
                .stream()
                .map(frontTask -> {
                    MainProductionResource mainProductionResource = new MainProductionResource()
                            .setId(frontTask.getMainResourceId())
                            .setWorkCalendars(workCalendarMap.get(frontTask.getMainResourceId()));
                    return BeanCopyUtil.copy(frontTask, ProductionTask.class)
                            .setQuantity(frontTask.getScheduledQuantity())
                            .setProcess(frontTask.getProcessInfo())
                            .setResource(mainProductionResource);
                })
                .toList();
        TaskTimeService taskTimeService = planningDirectionHandler.getTaskTimeService(PlanningDirectionEnum.FORWARD);
        Queue<TaskDTO> queue = new ArrayDeque<>();
        taskAdjustList.forEach(queue::offer);
        // 移动后，前一个任务的结束时间
        LocalDateTime previousEndTime = null;
        TaskDTO resourcePreviousTask = null;
        while (!queue.isEmpty()) {
            TaskDTO task = queue.poll();

            // 已锁定的任务不重新计算时间
            if (task.getPinned()) {
                previousEndTime = task.getEndTime();
                resourcePreviousTask = task;
                continue;
            }

            List<WorkCalendar> workCalendarList = workCalendarMap.get(task.getMainResourceId());
            if (previousEndTime != null) {
                ProductionTask productionTask = BeanCopyUtil.copy(task, ProductionTask.class);
                productionTask.setProcess(task.getProcessInfo());
                productionTask.setResource(new MainProductionResource().setId(task.getId()).setWorkCalendars(workCalendarList));
                ProductionTask resourcePreviousProductionTask = BeanCopyUtil.copy(resourcePreviousTask, ProductionTask.class).setProcess(resourcePreviousTask.getProcessInfo());
                LocalDateTime startTime = taskTimeService.calculateStartTime(productionTask, resourcePreviousProductionTask, previousEndTime, productionTasks, workCalendarList, null);
                task.setStartTime(startTime);
            }
            // 计算结束时间
            Duration taskDuration = TaskDurationUtils.calculateTaskDuration(task.getMainResourceId(), task.getProcessInfo(), task.getScheduledQuantity());
            LocalDateTime endTime = TaskCalendarUtils.calculateEndTime(task.getStartTime(), taskDuration, workCalendarList);
            task.setEndTime(endTime);
            previousEndTime = endTime;
            resourcePreviousTask = task;
        }
    }

    /**
     * 转换任务信息为资源甘特任务图信息
     *
     * @param tasks 任务集合
     * @return 甘特图中任务信息
     */
    public List<ResourceGanttTaskVO> convertResourceGanttTaskList(List<TaskDTO> tasks) {
        // 查询合并任务的子任务，将不在任务集合中的子任务，插入到集合中
        Set<String> taskCodes = new HashSet<>();
        Set<String> mergeTaskCodes = new HashSet<>();
        tasks.forEach(task -> {
            taskCodes.add(task.getCode());
            if (task.getMergeTask()) {
                mergeTaskCodes.add(task.getCode());
            }
        });
        List<TaskDTO> childTasks = taskAdjustFacadeService.listTaskByMergeTaskCodes(mergeTaskCodes).stream()
                .filter(task -> !task.getDiscard())
                .filter(task -> !taskCodes.contains(task.getCode()))
                .map(task -> BeanCopyUtil.copy(task, TaskDTO.class))
                .toList();
        if (ObjectNull.isNotNull(childTasks)) {
            tasks.addAll(childTasks);
        }

        // 转换为前端可显示的数据结构
        Set<String> orderIds = new HashSet<>();
        Set<String> resourceIds = new HashSet<>();
        tasks.forEach(task -> {
            orderIds.add(task.getProductionOrderId());
            resourceIds.add(task.getMainResourceId());
        });


        // 获取资源可用日历
        Map<String, List<WorkCalendar>> workCalendarMap = workCalendarFacadeService.listResourceScheduleCalendar(resourceIds);
        // 转换为甘特图中的任务信息
        List<ProductionResourcePO> resources = productionResourceService.list(Wrappers.<ProductionResourcePO>lambdaQuery().in(ProductionResourcePO::getId, resourceIds));
        List<PlanTaskOrderPO> planTaskOrderList = planTaskOrderService.listByOrderIds(orderIds);
        DetailReportFieldSettingVO detailReportFieldSetting = reportHandler.getReportService(ReportTypeEnum.PLAN_RESOURCE_TASK_GANTT).getReportFieldSettings();
        return PlanUtils.convertGanttTaskList(tasks, detailReportFieldSetting, resources, planTaskOrderList, workCalendarMap);
    }

}
