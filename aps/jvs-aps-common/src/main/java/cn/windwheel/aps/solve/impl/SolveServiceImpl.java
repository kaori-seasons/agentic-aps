package cn.windwheel.aps.solve.impl;

import cn.windwheel.aps.dto.schedule.TaskDTO;
import cn.windwheel.aps.entity.PlanTaskAdjustPO;
import cn.windwheel.aps.entity.dto.planning.StrategyOrderSchedulingRuleDTO;
import cn.windwheel.aps.entity.enums.OrderSchedulingStatusEnum;
import cn.windwheel.aps.entity.enums.PlanningDirectionEnum;
import cn.windwheel.aps.entity.enums.ResourceStatusEnum;
import cn.windwheel.aps.entity.enums.SortRuleEnum;
import cn.windwheel.aps.graph.Graph;
import cn.windwheel.aps.graph.GraphUtils;
import cn.windwheel.aps.service.facade.PlanSolutionFacadeService;
import cn.windwheel.aps.solve.SolveProgressService;
import cn.windwheel.aps.solve.SolveService;
import cn.windwheel.aps.solve.component.InitSchedulingRuleDataComponent;
import cn.windwheel.aps.solve.component.RuleField;
import cn.windwheel.aps.solve.enums.SolveProgressStatusEnum;
import cn.windwheel.aps.solve.model.*;
import cn.windwheel.aps.solve.util.MrpUtils;
import cn.windwheel.aps.solve.util.ProductionTaskUtils;
import cn.windwheel.aps.solve.util.TaskValidatorUtils;
import cn.windwheel.common.constant.SysConstant;
import cn.windwheel.common.exception.BusinessException;
import cn.windwheel.common.utils.BeanCopyUtil;
import cn.windwheel.common.utils.ObjectNull;
import cn.windwheel.common.utils.TenantContextHolder;
import cn.windwheel.common.utils.function.Get;
import cn.windwheel.redis.utils.RedisUtils;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * @author jvs
 * 排产求解
 */
@Slf4j
@Service
@AllArgsConstructor
public class SolveServiceImpl implements SolveService {

    private final cn.windwheel.aps.solve.SolveAuditService solveAuditService;
    private final RedisUtils redisUtils;
    private final SolveProgressService solveProgressService;
    private final PlanSolutionFacadeService planSolutionFacadeService;
    private final cn.windwheel.aps.solve.config.SchedulingEngineRouter engineRouter;
    private final cn.windwheel.aps.solve.presolve.DynamicSchedulingService dynamicSchedulingService;

    /**
     * 排产任务id缓存
     */
    private static final String SOLVE_PLAN_ID_KEY = "smart:planning:generate:planId";

    @Async
    @Override
    public void solve(UUID planId, PlanningStrategy strategy, BasicData basicData) {
        boolean candidateSaved = false;
        try {
            String planKey = getPlanCacheKey();
            if (!planId.toString().equals(String.valueOf(redisUtils.get(planKey)))) { return; }
            solveAuditService.start(planId, strategy, basicData);
            if (redisUtils.exists(planKey + ":cancel:" + planId)) {
                solveAuditService.finish(planId,"CANCELLED",Map.of("message","用户已取消求解"));
                return;
            }
            // 构造规划解决方案
            SchedulingSolution solution = buildSolution(strategy, basicData);
            dynamicSchedulingService.apply(solution, basicData);
            solveAuditService.recordProblem(planId, solution);
            // 执行规划求解计算
            if (ObjectNull.isNull(solution.getTasks())) {
                // 修改排产进度
                solveProgressService.addProgressLog(SolveProgressStatusEnum.NO_SCHEDULED, "没有需要排程的任务");
                solveAuditService.finish(planId,"NO_SCHEDULED", Map.of("message","没有需要排程的任务"));
                return;
            }
            // 保存排产进度
            String msg = String.format("待分派任务总数：%s", solution.getTasks().size());
            solveProgressService.addProgressLog(SolveProgressStatusEnum.SCHEDULING, msg);
            // 设置排产求解开始时间
            solveProgressService.setProgressSolveStartTime(LocalDateTime.now());
            // 记录使用策略
            String profileMsg = "使用的排产算法策略: " + (strategy.getConfig().getSolverProfile() != null ? strategy.getConfig().getSolverProfile().getDesc() : "默认");
            solveProgressService.addProgressLog(SolveProgressStatusEnum.SCHEDULING, profileMsg);
            // 排产求解
            String cancelKey = getPlanCacheKey() + ":cancel:" + planId;
            SchedulingSolution apsSolutionResult = engineRouter.solve(planId, solution, () -> redisUtils.exists(cancelKey));
            if (ObjectNull.isNull(apsSolutionResult) || redisUtils.exists(getPlanCacheKey() + ":cancel:" + planId)) {
                solveAuditService.finish(planId,"CANCELLED",Map.of("message","用户已取消求解"));
                return;
            }
            if (apsSolutionResult.getScore().initScore() != 0 || apsSolutionResult.getScore().hardScore() < 0) {
                throw new BusinessException("未找到可行的计划");
            }

            // 校验排程结果是否合规
            validateSchedulingSolution(apsSolutionResult);
            Map<String,Object> report = solveAuditService.report(apsSolutionResult,basicData);
            String lockKey = SysConstant.redisKey("smart:planning:generate:lock", TenantContextHolder.getTenantId());
            if (!redisUtils.tryLock(lockKey, 60)) { throw new BusinessException("计划正在被确认或取消，请重试"); }
            try {
                if (!planId.toString().equals(String.valueOf(redisUtils.get(getPlanCacheKey())))
                        || redisUtils.exists(getPlanCacheKey() + ":cancel:" + planId)) {
                    solveAuditService.finish(planId,"CANCELLED",Map.of("message","用户已取消求解"));
                    return;
                }
                solveAuditService.finish(planId,"FEASIBLE",report);
                planSolutionFacadeService.saveSolutionPending(basicData, apsSolutionResult);
                candidateSaved = true;
            } finally {
                redisUtils.unLock(lockKey);
            }
            solveProgressService.addProgressLog(SolveProgressStatusEnum.SCHEDULING, "求解审计ID：" + planId);
            // 修改排产进度
            solveProgressService.addProgressLog(SolveProgressStatusEnum.SUCCESS,"排程已完成");
        } catch (Exception | StackOverflowError e) {
            if (candidateSaved) {
                log.error("候选计划已保存，进度通知失败, planId={}",planId,e);
                return;
            }
            if (e instanceof InterruptedException) { Thread.currentThread().interrupt(); }
            try {
                solveAuditService.finish(planId,"FAILED",Map.of("message",String.valueOf(e.getMessage())));
            } catch (Exception auditFailure) {
                log.error("保存排产失败审计时异常, planId={}",planId,auditFailure);
            }
            log.error("排产异常：", e);
            solveProgressService.addProgressLog(SolveProgressStatusEnum.FAILURE,"排程失败：" + e.getMessage());
            throw new BusinessException(e.getMessage());
        } finally {
            if (redisUtils.deleteIfValue(getPlanCacheKey(), planId.toString())) {
                if (redisUtils.exists(getPlanCacheKey() + ":cancel:" + planId)) {
                    solveProgressService.removeProgress();
                    redisUtils.del(getPlanCacheKey() + ":cancel:" + planId);
                }
            }
        }
    }

    @Override
    public boolean existsSolving() {
        return redisUtils.exists(getPlanCacheKey());
    }

    @Override
    public void cancel() {
        Object value = redisUtils.get(getPlanCacheKey());
        if (ObjectNull.isNull(value)) {
            return;
        }
        UUID planId = UUID.fromString(value.toString());
        redisUtils.set(getPlanCacheKey() + ":cancel:" + planId, true, 7200L);
        engineRouter.cancel(planId);
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

        // 计算计划时间
        solution.setPlanTime(calculatePlanTime(strategy, basicData.getProductionOrders()));

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
     * 计算计划时间
     *
     * @param strategy 策略
     * @param productionOrders 订单
     * @return 计划时间
     */
    private LocalDateTime calculatePlanTime(PlanningStrategy strategy, List<ProductionOrder> productionOrders) {
        if (PlanningDirectionEnum.BACKWARD.equals(strategy.getConfig().getDirection())) {
            Optional<LocalDateTime> latestDeliveryTime = Optional.ofNullable(productionOrders)
                    .orElseGet(Collections::emptyList)
                    .stream()
                    .map(ProductionOrder::getDeliveryTime)
                    .filter(ObjectNull::isNotNull)
                    .max(Comparator.naturalOrder());
            if (latestDeliveryTime.isPresent()) {
                return latestDeliveryTime.get();
            }
        }
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
        // 补充订单由其主订单的MRP重新生成，不能再次作为独立需求扣减库存。
        List<ProductionOrder> orders = basicData.getProductionOrders().stream()
                .filter(order -> !Boolean.TRUE.equals(order.getSupplement()))
                .collect(Collectors.toCollection(ArrayList::new));
        basicData.setProductionOrders(orders);
        solution.setProductionOrders(orders);
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
        // 冻结任务同样参与冲突判定，即使本次MRP没有生成它所属的工序。
        Set<String> generated = productionTaskList.stream().map(ProductionTask::getCode).collect(Collectors.toSet());
        pinnedProductionTaskList.stream().filter(task -> !generated.contains(task.getCode())).forEach(productionTaskList::add);
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
        cn.windwheel.aps.solve.util.SchedulingOrderNormalizer.appendSupplementOrders(orders, productionTaskList);
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
                            .setProcess(pinnedTask.getProcessInfo())
                            .setFrozenEndTime(pinnedTask.getEndTime());
                    Optional<MainProductionResource> mainResource = mainResources.stream()
                            .filter(resource -> pinnedTask.getMainResourceId().equals(resource.getId()))
                            .findFirst();
                    mainResource.ifPresent(productionTask::setResource);
                    if (ObjectNull.isNotNull(pinnedTask.getProductionOrderId())) {
                        ProductionOrder productionOrder = productionOrders.stream()
                                .filter(order -> order.getId().equals(pinnedTask.getProductionOrderId()))
                                .findFirst()
                                .orElse(null);
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
        for (ProductionTask task : apsSolutionResult.getTasks()) {
            if (cn.windwheel.aps.solve.score.SchedulingRules.active(task)
                    && (!cn.windwheel.aps.solve.score.SchedulingRules.scheduled(task)
                    || cn.windwheel.aps.solve.score.SchedulingRules.unavailable(task)
                    || cn.windwheel.aps.solve.score.SchedulingRules.frozenChanged(task))) {
                throw new BusinessException("计划包含未分配或违反冻结/不可用时间窗的任务");
            }
        }
        // 校验是否有不合规的任务
        List<TaskDTO> tasks = apsSolutionResult.getTasks().stream()
                .map(task ->
                        cn.windwheel.aps.solve.util.SchedulingTaskMapper.toDTO(task)
                                .setProductionOrderId(Optional.ofNullable(task.getOrder()).map(ProductionOrder::getId).orElseGet(() -> null))
                                .setColor(Optional.ofNullable(task.getOrder()).map(ProductionOrder::getColor).orElseGet(() -> null))
                                .setScheduledQuantity(task.getQuantity())
                                .setMainResourceId(task.getResource().getId())
                                .setProcessInfo(task.getProcess())
                                .setPinned(task.getPinned())
                                .setDiscard(false)
                )
                .toList();
        if (ObjectNull.isNotNull(TaskValidatorUtils.checkTaskCompliance(tasks))) {
            throw new BusinessException("部分任务安排不合规");
        }
    }
}
