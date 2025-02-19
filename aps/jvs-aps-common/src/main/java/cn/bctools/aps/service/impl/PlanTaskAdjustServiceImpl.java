package cn.bctools.aps.service.impl;

import cn.bctools.aps.entity.PlanTaskAdjustPO;
import cn.bctools.aps.entity.enums.PlanTaskStatusEnum;
import cn.bctools.aps.mapper.PlanTaskAdjustMapper;
import cn.bctools.aps.service.PlanTaskAdjustService;
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
 * 待处理的任务调整信息
 */
@Service
public class PlanTaskAdjustServiceImpl extends ServiceImpl<PlanTaskAdjustMapper, PlanTaskAdjustPO> implements PlanTaskAdjustService {

    @Override
    public PlanTaskAdjustPO getByCode(String taskCode) {
        return getOne(Wrappers.<PlanTaskAdjustPO>lambdaQuery().eq(PlanTaskAdjustPO::getCode, taskCode));
    }

    @Override
    public List<PlanTaskAdjustPO> listSubTaskByCodes(Collection<String> taskCodes) {
        if (ObjectNull.isNull(taskCodes)) {
            return Collections.emptyList();
        }
        return list(Wrappers.<PlanTaskAdjustPO>lambdaQuery().in(PlanTaskAdjustPO::getCode, taskCodes).or().in(PlanTaskAdjustPO::getOriginTaskCode, taskCodes));
    }

    @Override
    public List<PlanTaskAdjustPO> listByCodes(Collection<String> taskCodes) {
        if (ObjectNull.isNull(taskCodes)) {
            return Collections.emptyList();
        }
        return list(Wrappers.<PlanTaskAdjustPO>lambdaQuery().in(PlanTaskAdjustPO::getCode, taskCodes));
    }

    @Override
    public List<PlanTaskAdjustPO> listTaskByMergeTaskCodes(Collection<String> mergeTaskCodes) {
        if (ObjectNull.isNull(mergeTaskCodes)) {
            return Collections.emptyList();
        }
        return list(Wrappers.<PlanTaskAdjustPO>lambdaQuery()
                .in(PlanTaskAdjustPO::getMergeTaskCode, mergeTaskCodes));

    }

    @Override
    public LocalDateTime getEarliestTaskStartTime() {
        return Optional.ofNullable(getOne(Wrappers.<PlanTaskAdjustPO>lambdaQuery()
                        .select(PlanTaskAdjustPO::getStartTime)
                        .ne(PlanTaskAdjustPO::getTaskStatus, PlanTaskStatusEnum.COMPLETED)
                        .orderByAsc(PlanTaskAdjustPO::getStartTime).last("limit 1")))
                .map(PlanTaskAdjustPO::getStartTime)
                .orElseGet(() -> null);
    }
}
