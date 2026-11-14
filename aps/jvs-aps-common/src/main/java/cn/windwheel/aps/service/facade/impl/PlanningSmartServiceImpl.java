package cn.windwheel.aps.service.facade.impl;

import cn.windwheel.aps.dto.GeneratePlanningSmartDTO;
import cn.windwheel.aps.entity.PlanningStrategyPO;
import cn.windwheel.aps.service.PlanInfoService;
import cn.windwheel.aps.service.PlanningStrategyService;
import cn.windwheel.aps.service.facade.PlanSolutionFacadeService;
import cn.windwheel.aps.service.facade.PlanningSmartService;
import cn.windwheel.aps.solve.SolveProgressService;
import cn.windwheel.aps.solve.SolveService;
import cn.windwheel.aps.solve.component.BasicDataComponent;
import cn.windwheel.aps.solve.dto.SolveProgressDTO;
import cn.windwheel.aps.solve.enums.SolveProgressStatusEnum;
import cn.windwheel.aps.solve.model.BasicData;
import cn.windwheel.aps.solve.model.PlanningStrategy;
import cn.windwheel.aps.vo.schedule.PlanProgressVO;
import cn.windwheel.common.constant.SysConstant;
import cn.windwheel.common.exception.BusinessException;
import cn.windwheel.common.utils.BeanCopyUtil;
import cn.windwheel.common.utils.ObjectNull;
import cn.windwheel.common.utils.TenantContextHolder;
import cn.windwheel.redis.utils.RedisUtils;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * @author jvs
 * 智能排产
 */
@Slf4j
@Service
@AllArgsConstructor
public class PlanningSmartServiceImpl implements PlanningSmartService {


    /**
     * 生成排产任务锁
     */
    private static final String LOCK_KEY = "smart:planning:generate:lock";
    private static final Integer LOCK_TIME = 7 * 24 * 60 * 60;

    private final cn.windwheel.aps.solve.presolve.DynamicSchedulingService dynamicSchedulingService;
    private final cn.windwheel.aps.solve.SolveAuditService solveAuditService;
    private final RedisUtils redisUtils;
    private final SolveService solveService;
    private final PlanningStrategyService planningStrategyService;
    private final BasicDataComponent basicDataComponent;
    private final SolveProgressService solveProgressService;
    private final PlanSolutionFacadeService planSolutionFacadeService;
    private final PlanInfoService planInfoService;

    @Transactional(rollbackFor = Exception.class)
    @Override
    public void generate(GeneratePlanningSmartDTO generatePlanningSmart) {
        // 一个租户，在生成一次排产计划后，不论过了多久，只要没取消、确认计划， 就一直保留计划， 每次都能看到该计划
        // 一个租户，在已有待确认的排产计划时，不能再生成新的排产计划
        String tenantId = TenantContextHolder.getTenantId();
        String locKey = SysConstant.redisKey(LOCK_KEY, tenantId);
        if (!redisUtils.tryLock(locKey, LOCK_TIME)) {
            throw new BusinessException("排产计划正在生成中,请稍后再试");
        }
        try {
            if (solveService.existsSolving()) {
                throw new BusinessException("排产计划正在生成中,请稍后再试");
            }
            if (planSolutionFacadeService.existsUnconfirmedTask()) {
                throw new BusinessException("已有待确认的排产计划,不能重新生成");
            }
            PlanningStrategyPO planningStrategy = Optional.ofNullable(planningStrategyService.getById(generatePlanningSmart.getPlanningStrategyId()))
                    .orElseThrow(() -> new BusinessException("策略不存在"));
            if (!planningStrategy.getActive()) {
                throw new BusinessException("策略未生效");
            }
            PlanningStrategy strategy = BeanCopyUtil.copy(planningStrategy, PlanningStrategy.class);
            // 初始化排产相关的基础数据
            BasicData basicData = basicDataComponent.getBasicData();
            cn.windwheel.aps.solve.config.SolveManageConfigurator.validate(strategy.getConfig());
            basicData.setSourceSignature(solveAuditService.signature(basicData));
            dynamicSchedulingService.prepare(basicData, strategy, generatePlanningSmart.getDynamicRequest());
            UUID planId = UUID.randomUUID();
            String planKey = SysConstant.redisKey("smart:planning:generate:planId", tenantId);
            if (!Boolean.TRUE.equals(redisUtils.set(planKey, planId.toString(), 7200L))) {
                throw new BusinessException("无法预留排产会话");
            }
            try {
                solveProgressService.initProgress(planKey);
                solveService.solve(planId, strategy, basicData);
            } catch (Exception dispatchFailure) {
                redisUtils.deleteIfValue(planKey, planId.toString());
                solveProgressService.removeProgress();
                throw dispatchFailure;
            }
        } catch (Exception e) {
            log.error("排产异常：", e);
            throw new BusinessException(e.getMessage());
        } finally {
            redisUtils.unLock(locKey);
        }
    }


    @Override
    public PlanProgressVO getPlanProgress() {
        // 获取进度
        SolveProgressDTO progress = solveProgressService.getProgress();
        PlanProgressVO planProgressVO = BeanCopyUtil.copy(progress, PlanProgressVO.class);
        if (SolveProgressStatusEnum.NONE.equals(progress.getStatus())) {
            return planProgressVO;
        }

        // 获取进度详情
        List<Object> progressLogs = solveProgressService.listProgressLog();
        // 排产求解是异步执行的，且可能比较耗时，所以在查询进度时动态计算求解步骤的耗时
        if (SolveProgressStatusEnum.SCHEDULING.equals(progress.getStatus()) && ObjectNull.isNotNull(progress.getSolveStartTime())) {
            String format = solveProgressService.formatProcessingTime(progress.getSolveStartTime(), LocalDateTime.now());
            String content = "排程计算中" + format;
            progressLogs.add(content);
        }

        // 排产完成，添加日志
        if (SolveProgressStatusEnum.SUCCESS.equals(progress.getStatus())) {
            String content = "点击【预览】可查看排程结果；点击【放弃】将取消本次排程；点击【提交】将更新计划。";
            progressLogs.add(content);
        }

        planProgressVO.setContents(progressLogs);
        return planProgressVO;
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public void cancelPlanPending() {
        String lockKey = SysConstant.redisKey(LOCK_KEY, TenantContextHolder.getTenantId());
        if (!redisUtils.tryLock(lockKey, 60)) { throw new BusinessException("计划正在被更新，请稍后重试"); }
        try {
            solveService.cancel();
            solveAuditService.markReviewed("CANCELLED");
            planSolutionFacadeService.removeSolutionPending();
            planInfoService.removeUnconfirmedPlan();
            solveProgressService.removeProgress();
        } finally {
            redisUtils.unLock(lockKey);
        }
    }

    @Transactional(rollbackFor = Exception.class, isolation = org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    @Override
    public void confirmPlan() {
        String lockKey = SysConstant.redisKey(LOCK_KEY, TenantContextHolder.getTenantId());
        if (!redisUtils.tryLock(lockKey, 60)) { throw new BusinessException("计划正在被更新，请稍后重试"); }
        try {
            if (solveService.existsSolving()) { throw new BusinessException("求解尚未结束，不能确认计划"); }
            solveAuditService.validateBeforeConfirm(basicDataComponent.getBasicDataForConfirmation());
            planSolutionFacadeService.saveSolution();
            solveAuditService.markReviewed("CONFIRMED");
            solveProgressService.removeProgress();
        } finally {
            redisUtils.unLock(lockKey);
        }
    }
}
