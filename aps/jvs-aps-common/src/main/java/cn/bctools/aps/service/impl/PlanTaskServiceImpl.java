package cn.bctools.aps.service.impl;

import cn.bctools.aps.entity.PlanTaskPO;
import cn.bctools.aps.entity.enums.PlanTaskStatusEnum;
import cn.bctools.aps.mapper.PlanTaskMapper;
import cn.bctools.aps.service.PlanTaskService;
import cn.bctools.common.exception.BusinessException;
import cn.bctools.common.utils.ObjectNull;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * @author jvs
 * 排产计划任务
 */
@Service
public class PlanTaskServiceImpl extends ServiceImpl<PlanTaskMapper, PlanTaskPO> implements PlanTaskService {

    @Override
    public PlanTaskPO getByCode(String taskCode) {
        PlanTaskPO planTask = getOne(Wrappers.<PlanTaskPO>lambdaQuery().eq(PlanTaskPO::getCode, taskCode));
        return Optional.ofNullable(planTask).orElseThrow(() -> new BusinessException("任务不存在"));
    }

    @Override
    public List<PlanTaskPO> listSubTaskByCodes(Collection<String> taskCodes) {
        if (ObjectNull.isNull(taskCodes)) {
            return Collections.emptyList();
        }
        return list(Wrappers.<PlanTaskPO>lambdaQuery().in(PlanTaskPO::getCode, taskCodes).or().in(PlanTaskPO::getOriginTaskCode, taskCodes));
    }

    @Override
    public List<PlanTaskPO> listByCodes(Collection<String> taskCodes) {
        if (ObjectNull.isNull(taskCodes)) {
            return Collections.emptyList();
        }
        return list(Wrappers.<PlanTaskPO>lambdaQuery().in(PlanTaskPO::getCode, taskCodes));
    }

    @Override
    public List<PlanTaskPO> listTaskByMergeTaskCodes(Collection<String> mergeTaskCodes) {
        if (ObjectNull.isNull(mergeTaskCodes)) {
            return Collections.emptyList();
        }
        return list(Wrappers.<PlanTaskPO>lambdaQuery()
                .in(PlanTaskPO::getMergeTaskCode, mergeTaskCodes));
    }

    @Override
    public List<PlanTaskPO> listTaskByMergeTaskCode(String mergeTaskCode) {
        return list(Wrappers.<PlanTaskPO>lambdaQuery()
                .eq(PlanTaskPO::getMergeTaskCode, mergeTaskCode));
    }

    @Override
    public LocalDateTime getEarliestTaskStartTime() {
        return Optional.ofNullable(getOne(Wrappers.<PlanTaskPO>lambdaQuery()
                        .select(PlanTaskPO::getStartTime)
                        .ne(PlanTaskPO::getTaskStatus, PlanTaskStatusEnum.COMPLETED)
                        .orderByAsc(PlanTaskPO::getStartTime).last("limit 1")))
                .map(PlanTaskPO::getStartTime)
                .orElseGet(() -> null);
    }
}
