package cn.bctools.aps.component;

import cn.bctools.aps.annotation.AdjustTaskFreeze;
import cn.bctools.aps.dto.schedule.AdjustTaskFreezeDTO;
import cn.bctools.aps.enums.TaskFreezeTypeEnum;
import cn.bctools.aps.service.facade.AdjustTaskFreezeFacadeService;
import cn.bctools.aps.vo.schedule.TaskAdjustFreezeVO;
import cn.bctools.common.exception.BusinessException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * @author jvs
 * 锁定/解锁任务统一处理类
 */
@Component
public class AdjustTaskFreezeHandler {

    private final Map<TaskFreezeTypeEnum, AdjustTaskFreezeFacadeService<? extends AdjustTaskFreezeDTO>> taskFreezeFacadeServiceMap = new EnumMap<>(TaskFreezeTypeEnum.class);

    @Autowired
    public AdjustTaskFreezeHandler(List<AdjustTaskFreezeFacadeService<? extends AdjustTaskFreezeDTO>> taskFreezeFacadeServiceList) {
        taskFreezeFacadeServiceList.forEach(service -> {
            AdjustTaskFreeze taskAdjustAnnotation = AnnotationUtils.findAnnotation(service.getClass(), AdjustTaskFreeze.class);
            if (taskAdjustAnnotation != null) {
                taskFreezeFacadeServiceMap.put(taskAdjustAnnotation.type(), service);
            }
        });
    }

    /**
     * 锁定/解锁任务
     *
     * @param freezeType 锁定/解锁方式
     * @param input     入参
     * @return 锁定/解锁结果
     */
    @Transactional(rollbackFor = Exception.class)
    public <T extends AdjustTaskFreezeDTO> TaskAdjustFreezeVO freeze(TaskFreezeTypeEnum freezeType, T input) {
        AdjustTaskFreezeFacadeService<T> service = getTaskAdjustService(freezeType);
        return service.freeze(input.getPinned(), input);
    }

    /**
     * 获取任务锁定/解锁服务
     *
     * @param freezeType 锁定/解锁方式
     * @return 任务锁定/解锁服务
     */
    @SuppressWarnings("unchecked")
    public <T extends AdjustTaskFreezeDTO> AdjustTaskFreezeFacadeService<T> getTaskAdjustService(TaskFreezeTypeEnum freezeType) {
        AdjustTaskFreezeFacadeService<T> service = (AdjustTaskFreezeFacadeService<T>) taskFreezeFacadeServiceMap.get(freezeType);
        if (service == null) {
            throw new BusinessException("不支持的任务锁定/解锁类型", freezeType);
        }
        return service;
    }
}
