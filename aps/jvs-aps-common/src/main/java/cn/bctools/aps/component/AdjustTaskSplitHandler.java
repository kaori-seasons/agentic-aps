package cn.bctools.aps.component;

import cn.bctools.aps.annotation.AdjustTaskSplit;
import cn.bctools.aps.dto.schedule.AdjustTaskSplitDTO;
import cn.bctools.aps.enums.TaskSplitTypeEnum;
import cn.bctools.aps.service.facade.AdjustTaskSplitFacadeService;
import cn.bctools.aps.vo.schedule.TaskAdjustSplitVO;
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
 * 拆分任务统一处理类
 */
@Component
public class AdjustTaskSplitHandler {

    private final Map<TaskSplitTypeEnum, AdjustTaskSplitFacadeService<? extends AdjustTaskSplitDTO>> taskSplitFacadeServiceMap = new EnumMap<>(TaskSplitTypeEnum.class);

    @Autowired
    public AdjustTaskSplitHandler(List<AdjustTaskSplitFacadeService<? extends AdjustTaskSplitDTO>> taskSplitFacadeServiceList) {
        taskSplitFacadeServiceList.forEach(service -> {
            AdjustTaskSplit taskAdjustAnnotation = AnnotationUtils.findAnnotation(service.getClass(), AdjustTaskSplit.class);
            if (taskAdjustAnnotation != null) {
                taskSplitFacadeServiceMap.put(taskAdjustAnnotation.type(), service);
            }
        });
    }

    /**
     * 拆分任务
     *
     * @param splitType 拆分方式
     * @param input     入参
     * @return 拆分结果
     */
    @Transactional(rollbackFor = Exception.class)
    public <T extends AdjustTaskSplitDTO> TaskAdjustSplitVO split(TaskSplitTypeEnum splitType, T input) {
        AdjustTaskSplitFacadeService<T> service = getTaskAdjustService(splitType);
        return service.split(input);
    }

    /**
     * 获取任务拆分服务
     *
     * @param splitType 拆分方式
     * @return 任务拆分服务
     */
    @SuppressWarnings("unchecked")
    public <T extends AdjustTaskSplitDTO> AdjustTaskSplitFacadeService<T> getTaskAdjustService(TaskSplitTypeEnum splitType) {
        AdjustTaskSplitFacadeService<T> service = (AdjustTaskSplitFacadeService<T>) taskSplitFacadeServiceMap.get(splitType);
        if (service == null) {
            throw new BusinessException("不支持的任务拆分类型", splitType);
        }
        return service;
    }
}
