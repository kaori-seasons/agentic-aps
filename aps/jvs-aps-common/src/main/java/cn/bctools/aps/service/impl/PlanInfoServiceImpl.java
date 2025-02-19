package cn.bctools.aps.service.impl;

import cn.bctools.aps.entity.PlanInfoPO;
import cn.bctools.aps.entity.enums.PlanInfoStatusEnum;
import cn.bctools.aps.mapper.PlanInfoMapper;
import cn.bctools.aps.service.PlanInfoService;
import cn.bctools.aps.solve.model.PlanningStrategy;
import cn.bctools.common.utils.ObjectNull;
import cn.bctools.common.utils.TenantContextHolder;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * @author jvs
 */
@Service
public class PlanInfoServiceImpl extends ServiceImpl<PlanInfoMapper, PlanInfoPO> implements PlanInfoService {

    @Override
    public void createUnconfirmedPlan(PlanningStrategy strategy) {
        PlanInfoPO planInfo = new PlanInfoPO()
                .setScheduleStartTime(strategy.getBeginTime())
                .setPlanStatus(PlanInfoStatusEnum.UNCONFIRMED);
        save(planInfo);
    }

    @Override
    public void confirmedPlan(LocalDateTime earliestTaskStartTime) {
        List<PlanInfoPO> planInfoList = list(Wrappers.<PlanInfoPO>lambdaQuery()
                .eq(PlanInfoPO::getTenantId, TenantContextHolder.getTenantId()));

        PlanInfoPO confirmedPlan = null;
        PlanInfoPO unconfirmedPlan = null;
        for (PlanInfoPO planInfo : planInfoList) {
            if (PlanInfoStatusEnum.CONFIRMED.equals(planInfo.getPlanStatus())) {
                confirmedPlan = planInfo;
            } else {
                unconfirmedPlan = planInfo;
            }
        }

        // 同步已确认任务的部分数据到待确认任务
        if (ObjectNull.isNotNull(confirmedPlan)) {
            unconfirmedPlan.setLastTaskAssignmentTime(confirmedPlan.getLastTaskAssignmentTime());
        }
        // 将待确认任务，修改为确认任务
        unconfirmedPlan.setPlanStatus(PlanInfoStatusEnum.CONFIRMED);
        unconfirmedPlan.setEarliestTaskStartTime(earliestTaskStartTime);

        // 删除当前租户的计划任务
        remove(Wrappers.<PlanInfoPO>lambdaQuery()
                .eq(PlanInfoPO::getTenantId, TenantContextHolder.getTenantId()));

        // 保存确认任务
        save(unconfirmedPlan);
    }

    @Override
    public void removeUnconfirmedPlan() {
        remove(Wrappers.<PlanInfoPO>lambdaQuery()
                .eq(PlanInfoPO::getPlanStatus, PlanInfoStatusEnum.UNCONFIRMED));
    }

    @Override
    public void updateAssignmentTime(LocalDateTime lastTaskAssignmentTime) {
        update(Wrappers.<PlanInfoPO>lambdaUpdate()
                .set(PlanInfoPO::getLastTaskAssignmentTime, lastTaskAssignmentTime)
                .eq(PlanInfoPO::getPlanStatus, PlanInfoStatusEnum.CONFIRMED));
    }

    @Override
    public PlanInfoPO getConfirmedPlan() {
        return getOne(Wrappers.<PlanInfoPO>lambdaQuery()
                .eq(PlanInfoPO::getPlanStatus, PlanInfoStatusEnum.CONFIRMED)
                .eq(PlanInfoPO::getTenantId, TenantContextHolder.getTenantId()));
    }
}
