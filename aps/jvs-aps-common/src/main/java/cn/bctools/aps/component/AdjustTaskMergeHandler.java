package cn.bctools.aps.component;

import cn.bctools.aps.annotation.AdjustTaskMerge;
import cn.bctools.aps.dto.schedule.AdjustTaskMergeDTO;
import cn.bctools.aps.enums.TaskMergeTypeEnum;
import cn.bctools.aps.service.facade.AdjustTaskMergeFacadeService;
import cn.bctools.aps.vo.schedule.TaskAdjustMergeVO;
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
 * 合并任务统一处理类
 */
@Component
public class AdjustTaskMergeHandler {

    private final Map<TaskMergeTypeEnum, AdjustTaskMergeFacadeService<? extends AdjustTaskMergeDTO>> taskMergeFacadeServiceMap = new EnumMap<>(TaskMergeTypeEnum.class);

    @Autowired
    public AdjustTaskMergeHandler(List<AdjustTaskMergeFacadeService<? extends AdjustTaskMergeDTO>> taskMergeFacadeServiceList) {
        taskMergeFacadeServiceList.forEach(service -> {
            AdjustTaskMerge taskAdjustAnnotation = AnnotationUtils.findAnnotation(service.getClass(), AdjustTaskMerge.class);
            if (taskAdjustAnnotation != null) {
                taskMergeFacadeServiceMap.put(taskAdjustAnnotation.type(), service);
            }
        });
    }

    /**
     * 合并任务
     *
     * @param mergeType 合并方式
     * @param input    入参
     * @return 合并结果
     */
    @Transactional(rollbackFor = Exception.class)
    public <T extends AdjustTaskMergeDTO> TaskAdjustMergeVO merge(TaskMergeTypeEnum mergeType, T input) {
        AdjustTaskMergeFacadeService<T> service = getTaskAdjustService(mergeType);
        return service.merge(input);
    }

    /**
     * 获取任务合并服务
     *
     * @param mergeType 合并方式
     * @return 任务合并服务
     */
    @SuppressWarnings("unchecked")
    public <T extends AdjustTaskMergeDTO> AdjustTaskMergeFacadeService<T> getTaskAdjustService(TaskMergeTypeEnum mergeType) {
        AdjustTaskMergeFacadeService<T> service = (AdjustTaskMergeFacadeService<T>) taskMergeFacadeServiceMap.get(mergeType);
        if (service == null) {
            throw new BusinessException("不支持的任务合并类型", mergeType);
        }
        return service;
    }
}
