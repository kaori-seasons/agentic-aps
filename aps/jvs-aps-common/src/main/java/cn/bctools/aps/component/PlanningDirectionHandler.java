package cn.bctools.aps.component;

import cn.bctools.aps.annotation.PlanningDirection;
import cn.bctools.aps.entity.enums.PlanningDirectionEnum;
import cn.bctools.aps.solve.calculate.service.TaskTimeService;
import cn.bctools.common.exception.BusinessException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * @author jvs
 * 排程方向统一处理
 */
@Component
public class PlanningDirectionHandler {

    private final Map<PlanningDirectionEnum, TaskTimeService> taskTimeServiceMap = new EnumMap<>(PlanningDirectionEnum.class);

    @Autowired
    public PlanningDirectionHandler(List<TaskTimeService> taskTimeServiceList) {
        taskTimeServiceList.forEach(service -> {
            PlanningDirection directionAnnotation = AnnotationUtils.findAnnotation(service.getClass(), PlanningDirection.class);
            if (directionAnnotation != null) {
                taskTimeServiceMap.put(directionAnnotation.direction(), service);
            }
        });
    }

    /**
     * 获取排程方向计算实现
     *
     * @param direction 排程方向
     * @return 实现
     */
    public TaskTimeService getTaskTimeService(PlanningDirectionEnum direction) {
        if (!taskTimeServiceMap.containsKey(direction)) {
            throw new BusinessException("不支持的排程方向");
        }
        return taskTimeServiceMap.get(direction);
    }
}
