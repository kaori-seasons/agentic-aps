package cn.bctools.aps.component;

import cn.bctools.aps.annotation.AdjustTaskMove;
import cn.bctools.aps.dto.schedule.AdjustTaskMoveDTO;
import cn.bctools.aps.enums.TaskMoveTypeEnum;
import cn.bctools.aps.service.facade.AdjustTaskMoveFacadeService;
import cn.bctools.aps.vo.schedule.TaskAdjustMoveVO;
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
 * 移动任务统一处理类
 */
@Component
public class AdjustTaskMoveHandler {
    private final Map<TaskMoveTypeEnum, AdjustTaskMoveFacadeService<? extends AdjustTaskMoveDTO>> taskMoveFacadeServiceMap = new EnumMap<>(TaskMoveTypeEnum.class);

    @Autowired
    public AdjustTaskMoveHandler(List<AdjustTaskMoveFacadeService<? extends AdjustTaskMoveDTO>> taskMoveFacadeServiceList) {
        taskMoveFacadeServiceList.forEach(service -> {
            AdjustTaskMove taskAdjustAnnotation = AnnotationUtils.findAnnotation(service.getClass(), AdjustTaskMove.class);
            if (taskAdjustAnnotation != null) {
                taskMoveFacadeServiceMap.put(taskAdjustAnnotation.type(), service);
            }
        });
    }

    /**
     * 移动任务
     *
     * @param moveType 移动方式
     * @param input    入参
     * @return 移动结果
     */
    @Transactional(rollbackFor = Exception.class)
    public <T extends AdjustTaskMoveDTO> TaskAdjustMoveVO move(TaskMoveTypeEnum moveType, T input) {
        AdjustTaskMoveFacadeService<T> service = getTaskAdjustService(moveType);
        return service.move(input);
    }

    /**
     * 获取任务移动服务
     *
     * @param moveType 移动方式
     * @return 任务移动服务
     */
    @SuppressWarnings("unchecked")
    public <T extends AdjustTaskMoveDTO> AdjustTaskMoveFacadeService<T> getTaskAdjustService(TaskMoveTypeEnum moveType) {
        AdjustTaskMoveFacadeService<T> service = (AdjustTaskMoveFacadeService<T>) taskMoveFacadeServiceMap.get(moveType);
        if (service == null) {
            throw new BusinessException("不支持的任务移动类型", moveType);
        }
        return service;
    }
}
