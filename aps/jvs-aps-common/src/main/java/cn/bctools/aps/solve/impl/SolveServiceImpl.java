package cn.bctools.aps.solve.impl;

import cn.bctools.aps.dto.schedule.TaskDTO;
import cn.bctools.aps.entity.PlanTaskAdjustPO;
import cn.bctools.aps.entity.dto.planning.StrategyOrderSchedulingRuleDTO;
import cn.bctools.aps.entity.enums.OrderSchedulingStatusEnum;
import cn.bctools.aps.entity.enums.ResourceStatusEnum;
import cn.bctools.aps.entity.enums.SortRuleEnum;
import cn.bctools.aps.graph.Graph;
import cn.bctools.aps.graph.GraphUtils;
import cn.bctools.aps.service.facade.PlanSolutionFacadeService;
import cn.bctools.aps.solve.SolveProgressService;
import cn.bctools.aps.solve.SolveService;
import cn.bctools.aps.solve.component.InitSchedulingRuleDataComponent;
import cn.bctools.aps.solve.component.RuleField;
import cn.bctools.aps.solve.config.SolveManageConfigurator;
import cn.bctools.aps.solve.enums.SolveProgressStatusEnum;
import cn.bctools.aps.solve.model.*;
import cn.bctools.aps.solve.util.MrpUtils;
import cn.bctools.aps.solve.util.ProductionTaskUtils;
import cn.bctools.aps.solve.util.TaskValidatorUtils;
import cn.bctools.common.constant.SysConstant;
import cn.bctools.common.exception.BusinessException;
import cn.bctools.common.utils.BeanCopyUtil;
import cn.bctools.common.utils.ObjectNull;
import cn.bctools.common.utils.TenantContextHolder;
import cn.bctools.common.utils.function.Get;
import cn.bctools.redis.utils.RedisUtils;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.optaplanner.core.api.solver.SolverJob;
import org.optaplanner.core.api.solver.SolverManager;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

/**
 * @author jvs
 * 排产求解
 */
@Slf4j
@Service
@AllArgsConstructor
public class SolveServiceImpl implements SolveService {

    private final RedisUtils redisUtils;
    private final SolveProgressService solveProgressService;
    private final PlanSolutionFacadeService planSolutionFacadeService;
    private final SolveManageConfigurator solveManageConfigurator;

    /**
     * 排产任务id缓存
     */
    private static final String SOLVE_PLAN_ID_KEY = "smart:planning:generate:planId";

    @Async
    @Transactional(rollbackFor = Exception.class)
    @Override
    public void solve(UUID planId, PlanningStrategy strategy, BasicData basicData) {
        try {
            // 缓存求解任务id
            String planKey = getPlanCacheKey();
            redisUtils.set(getPlanCacheKey(), planId);
            // 初始化排产进度
            solveProgressService.initProgress(planKey);
            // 构造规划解决方案
            SchedulingSolution solution = buildSolution(strategy, basicData);
            // 执行规划求解计算
            if (ObjectNull.isNull(solution.getTasks())) {
                // 修改排产进度
                solveProgressService.addProgressLog(SolveProgressStatusEnum.NO_SCHEDULED, "没有需要排程的任务");
                // 将排产结果保存到待确认计划
                planSolutionFacadeService.saveSolutionPending(basicData, new SchedulingSolution());
                return;
            }
            // 保存排产进度
            String msg = String.format("待分派任务总数：%s", solution.getTasks().size());
            solveProgressService.addProgressLog(SolveProgressStatusEnum.SCHEDULING, msg);
            // 设置排产求解开始时间
            solveProgressService.setProgressSolveStartTime(LocalDateTime.now());
            // 排产求解
            SchedulingSolution apsSolutionResult = solveExecute(planId, solution);
            if (ObjectNull.isNull(apsSolutionResult)) {
                return;
            }
            if (apsSolutionResult.getScore().initScore() < 0 || apsSolutionResult.getScore().hardScore() < 0) {
                throw new BusinessException("未找到可行的计划");
            }
            // 置空主资源下的任务集合
            apsSolutionResult.getMainResources().forEach(mainResource -> mainResource.setTaskList(null));
            // 校验排程结果是否合规
            validateSchedulingSolution(apsSolutionResult);
            // 将排产结果保存到待确认计划
            planSolutionFacadeService.saveSolutionPending(basicData, apsSolutionResult);
            // 修改排产进度
            solveProgressService.addProgressLog(SolveProgressStatusEnum.SUCCESS,"排程已完成");
        } catch (Exception e) {
            log.error("排产异常：", e);
            solveProgressService.addProgressLog(SolveProgressStatusEnum.FAILURE,"排程失败：" + e.getMessage());
            throw new BusinessException(e.getMessage());
        } finally {
            redisUtils.del(getPlanCacheKey());
        }
    }

    @Override
    public boolean existsSolving() {
        return redisUtils.exists(getPlanCacheKey());
    }

    @Override
    public void cancel() {
        String value = (String) redisUtils.get(getPlanCacheKey());
        if (ObjectNull.isNull(value)) {
            return;
        }
        UUID planId = UUID.fromString(value);
        SolverManager<SchedulingSolution, UUID> solverManager = solveManageConfigurator.getSolverManager();
        if (ObjectNull.isNull(solverManager)) {
            return;
        }
        solverManager.terminateEarly(planId);
        // 删除求解任务缓存
        redisUtils.del(getPlanCacheKey());
    }

    /**
     * 排产求解计算
     *
     * @param planId  任务id
     * @param problem 规划解决方案
     * @return 排产结果
     */
    private SchedulingSolution solveExecute(UUID planId, SchedulingSolution problem) {
        SolverManager<SchedulingSolution, UUID> solverManager = solveManageConfigurator.initSolverManager(problem.getStrategy());
        log.debug("开始计算, 待排任务数量：{}", problem.getTasks().size());
        long t1 = System.currentTimeMillis();
        try {
            SolverJob<SchedulingSolution, UUID> solver = solverManager.solve(planId, problem);
            SchedulingSolution apsSolutionResult = solver.getFinalBestSolution();
            if (solver.isTerminatedEarly()) {
                return null;
            }
            log.debug("结束计算耗时：{}, 任务数量：{}", (System.currentTimeMillis() - t1), problem.getTasks().size());
            return apsSolutionResult;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException(e.getMessage());
        } catch (ExecutionException e) {
            throw new BusinessException(e.getMessage());
        } finally {
            solverManager.terminateEarly(planId);
        }
    }

    /**
     * 构造规划解决方案
     *
     * @param strategy  排产策略
     * @param basicData 基础数据
     * @return 规划解决方案
     */
    private SchedulingSolution buildSolution(PlanningStrategy strategy, BasicData basicData) {
        SchedulingSolution solution = new SchedulingSolution()
                .setStrategy(strategy)
                .setProductionOrders(basicData.getProductionOrders());

        // 计算计划开始时间
        solution.setPlanTime(calculatePlanTime(strategy));

        // 主资源
        fillMainProductionResources(solution, basicData.getProductionResources(), basicData.getWorkCalendarMap());

        // 处理和分派生产订单
        processAndAllocateProductionOrders(solution, basicData);

        // 将已锁定的任务保存到主资源的任务集合
        solution.getMainResources().forEach(resource -> {
            List<ProductionTask> tasks = solution.getTasks().stream()
                    .filter(task -> ObjectNull.isNotNull(task.getResource()))
                    .filter(task -> ObjectNull.isNull(task.getMergeTaskCode()))
                    .filter(task -> resource.getId().equals(task.getResource().getId()))
                    .distinct()
                    .toList();
            resource.getTaskList().addAll(tasks);
        });
        return solution;
    }

    /**
     * 计算计划开始时间
     *
     * @param strategy 策略
     * @return 计划开始时间
     */
    private LocalDateTime calculatePlanTime(PlanningStrategy strategy) {
        // 若计划开始时间在当前时间之前，则以当前时间作为计划开始时间
        if (strategy.getBeginTime().isBefore(LocalDateTime.now())) {
            return LocalDateTime.now();
        }
        return strategy.getBeginTime();
    }

    /**
     * 填充可用的主资源
     *
     * @param solution            规划解决方案
     * @param productionResources 资源集合
     * @param workCalendarMap     日历集合
     */
    private void fillMainProductionResources(SchedulingSolution solution, List<ProductionResource> productionResources, Map<String, WorkCalendar> workCalendarMap) {
        List<MainProductionResource> resources = productionResources.stream()
                .filter(resource -> ResourceStatusEnum.NORMAL.equals(resource.getResourceStatus()))
                .map(resource -> {
                    MainProductionResource mainResource = BeanCopyUtil.copy(resource, MainProductionResource.class);
                    // 填充主资源可用的日历
                    if (ObjectNull.isNotNull(resource.getWorkCalendarIds())) {
                        List<WorkCalendar> workCalendars = workCalendarMap.entrySet().stream()
                                .filter(e -> resource.getWorkCalendarIds().contains(e.getKey()))
                                .map(Map.Entry::getValue)
                                .toList();
                        mainResource.setWorkCalendars(workCalendars);
                    }
                    return mainResource;
                })
                .collect(Collectors.toList());
        if (ObjectNull.isNull(resources)) {
            throw new BusinessException("没有可用的主资源");
        }
        solution.setMainResources(resources);
    }


    /**
     * 处理和分派生产订单
     *
     * @param solution  规划解决方案
     * @param basicData 基础数据
     */
    private void processAndAllocateProductionOrders(SchedulingSolution solution, BasicData basicData) {
        // 已锁定的生产任务转换为排产任务数据结构
        List<ProductionTask> pinnedProductionTaskList = convertPinnedTask(solution.getMainResources(), basicData.getPinnedTasks(), basicData.getProductionOrders());
        // 生产任务
        List<ProductionTask> productionTaskList = new ArrayList<>();
        // 生产订单按规则排序
        List<ProductionOrder> orders = basicData.getProductionOrders();
        int orderSize = orders.size();
        sortProductionOrders(orders, solution.getStrategy().getOrderSchedulingRules());
        // 是否约束物料
        boolean materialConstrained = solution.getStrategy().getConfig().getMaterialConstrained();
        // 更新排产进度
        solveProgressService.saveProgressSolveStep(orderSize);
        solveProgressService.addProgressLog(SolveProgressStatusEnum.SCHEDULING,"排产订单总数：" + orderSize);
        // 遍历生产订单，分配库存或生成生产任务
        int step = 0;
        for (ProductionOrder order : orders) {
            long timeMillis = System.currentTimeMillis();
            OrderSchedulingStatusEnum schedulingStatus = OrderSchedulingStatusEnum.NO_SCHEDULED;
            // 执行MRP计算
            Graph<MrpMaterial> mrp = MrpUtils.calculateMaterialAvailability(materialConstrained, order, basicData);
            if (!GraphUtils.isEmpty(mrp)) {
                schedulingStatus = OrderSchedulingStatusEnum.SCHEDULED;
                // 生成制造任务
                List<ProductionTask> tasks = ProductionTaskUtils.generateTask(order, basicData, mrp, pinnedProductionTaskList);
                tasks.removeIf(orderTask -> productionTaskList.stream().anyMatch(task -> task.getCode().equals(orderTask.getCode())));
                for (ProductionTask task : tasks) {
                    task.setPriority(step);
                }
                productionTaskList.addAll(tasks);
            }
            order.setSchedulingStatus(schedulingStatus);
            // 更新排程进度
            step++;
            String logStatus = OrderSchedulingStatusEnum.NO_SCHEDULED.equals(schedulingStatus) ? "库存充足无需排产" : "待分派";
            String progressLog = String.format("拆解订单【%s/%s-%s】, %s", step, orderSize, order.getCode(), logStatus);
            solveProgressService.addProgressLog(SolveProgressStatusEnum.SCHEDULING, progressLog, System.currentTimeMillis() - timeMillis, step);
        }
        // 待排产的生产任务
        solution.setTasks(productionTaskList);
        // 提取自动创建的补充生产订单，加入生产订单集合
        addSupplementOrders(orders, productionTaskList);
    }

    /**
     * 提取自动创建的补充生产订单，加入生产订单集合
     *
     * @param orders 生产订单
     * @param productionTaskList 生产任务
     */
    private void addSupplementOrders(List<ProductionOrder> orders, List<ProductionTask> productionTaskList) {
        // 从补充制造任务中，提取自动创建的补充生产订单
        List<ProductionOrder> supplementOrders = productionTaskList.stream()
                .filter(ProductionTask::getSupplement)
                .map(ProductionTask::getOrder)
                .distinct()
                .toList();
        if (ObjectNull.isNull(supplementOrders)) {
           return;
        }
        // 若补充订单的订单号已存在，则移除旧的订单，加入新的订单
        Set<String> supplementOrderCodes = supplementOrders.stream()
                .map(ProductionOrder::getCode)
                .collect(Collectors.toSet());
        orders.removeIf(order -> supplementOrderCodes.contains(order.getCode()));
        orders.addAll(supplementOrders);
    }

    /**
     * 已锁定的生产任务转换为排产任务数据结构
     *
     * @param mainResources 主资源集合
     * @param pinnedTasks   已锁定的任务
     * @return 转换后排产任务数据结构
     */
    private List<ProductionTask> convertPinnedTask(List<MainProductionResource> mainResources, List<PlanTaskAdjustPO> pinnedTasks, List<ProductionOrder> productionOrders) {
        if (ObjectNull.isNull(pinnedTasks)) {
            return Collections.emptyList();
        }
        // 已锁定的任务转换为生产任务数据结构
        return pinnedTasks.stream()
                .map(pinnedTask -> {
                    ProductionTask productionTask = BeanCopyUtil.copy(pinnedTask, ProductionTask.class)
                            .setQuantity(pinnedTask.getScheduledQuantity())
                            .setProcess(pinnedTask.getProcessInfo());
                    Optional<MainProductionResource> mainResource = mainResources.stream()
                            .filter(resource -> pinnedTask.getMainResourceId().equals(resource.getId()))
                            .findFirst();
                    mainResource.ifPresent(productionTask::setResource);
                    if (ObjectNull.isNotNull(pinnedTask.getProductionOrderId())) {
                        ProductionOrder productionOrder = productionOrders.stream()
                                .filter(order -> order.getId().equals(pinnedTask.getProductionOrderId()))
                                .findFirst()
                                .get();
                        productionTask.setOrder(productionOrder);
                    }
                    return productionTask;
                })
                .collect(Collectors.toList());
    }

    /**
     * 生产订单排序
     *
     * @param orderList 生产订单
     * @param rules     规则
     */
    private void sortProductionOrders(List<ProductionOrder> orderList, List<StrategyOrderSchedulingRuleDTO> rules) {
        if (ObjectNull.isNull(orderList)) {
            throw new BusinessException("没有需要排产的订单");
        }
        // 如果未设置规则，设置默认规则
        if (ObjectNull.isNull(rules)) {
            rules = defaultOrderSortRule();
        }
        Map<String, RuleField> ruleFieldMap = InitSchedulingRuleDataComponent.getRuleFieldMap();
        Comparator<ProductionOrder> comparator = null;
        for (StrategyOrderSchedulingRuleDTO rule : rules) {
            RuleField ruleField = ruleFieldMap.get(rule.getFieldKey());
            Comparator<ProductionOrder> filedComparator = Comparator.comparing(ruleField.getGetter());
            if (SortRuleEnum.DESC.equals(rule.getSortRule())) {
                filedComparator = filedComparator.reversed();
            }
            comparator = comparator == null ? filedComparator : comparator.thenComparing(filedComparator);
        }
        orderList.sort(comparator);
    }

    /**
     * 未设置订单排序规则时，默认排序规则
     *
     * @return 订单排序规则
     */
    private List<StrategyOrderSchedulingRuleDTO> defaultOrderSortRule() {
        // 优先级高的排前面 + 需求交付时间近的排前面
        StrategyOrderSchedulingRuleDTO priorityRule = new StrategyOrderSchedulingRuleDTO()
                .setFieldKey(Get.name(ProductionOrder::getPriority))
                .setSortRule(SortRuleEnum.DESC);
        StrategyOrderSchedulingRuleDTO deliveryTimeRule = new StrategyOrderSchedulingRuleDTO()
                .setFieldKey(Get.name(ProductionOrder::getDeliveryTime))
                .setSortRule(SortRuleEnum.ASC);
        return Arrays.asList(priorityRule, deliveryTimeRule);
    }


    /**
     * 排产任务id缓存Key
     *
     * @return 排产任务id缓存Key
     */
    private String getPlanCacheKey() {
        return SysConstant.redisKey(SOLVE_PLAN_ID_KEY, TenantContextHolder.getTenantId());
    }

    /**
     * 校验排程结果是否合规
     *
     * @param apsSolutionResult 排程结果
     */
    private void validateSchedulingSolution(SchedulingSolution apsSolutionResult) {
        // 校验是否有不合规的任务
        List<TaskDTO> tasks = apsSolutionResult.getTasks().stream()
                .map(task ->
                        BeanCopyUtil.copy(task, TaskDTO.class)
                                .setProductionOrderId(Optional.ofNullable(task.getOrder()).map(ProductionOrder::getId).orElseGet(() -> null))
                                .setColor(Optional.ofNullable(task.getOrder()).map(ProductionOrder::getColor).orElseGet(() -> null))
                                .setScheduledQuantity(task.getQuantity())
                                .setMainResourceId(task.getResource().getId())
                                .setProcessInfo(task.getProcess())
                                .setPinned(false)
                )
                .toList();
        if (ObjectNull.isNotNull(TaskValidatorUtils.checkTaskCompliance(tasks))) {
            throw new BusinessException("部分任务安排不合规");
        }
    }
}
