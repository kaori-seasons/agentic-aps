package cn.bctools.aps.solve.model;

import cn.bctools.common.utils.ObjectNull;
import java.time.LocalDateTime;
import java.util.Comparator;

/**
 * 任务难度比较器，用于 FFD (First Fit Decreasing)
 * 难度越大的任务越先排程
 */
public class TaskDifficultyComparator implements Comparator<ProductionTask> {

    @Override
    public int compare(ProductionTask t1, ProductionTask t2) {
        if (ObjectNull.isNull(t1) && ObjectNull.isNull(t2)) {
            return 0;
        }
        if (ObjectNull.isNull(t1)) {
            return -1;
        }
        if (ObjectNull.isNull(t2)) {
            return 1;
        }

        // 1. 优先级 (值越小优先级越高，越应该先排，即"难度"越大)
        int priorityComparison = Integer.compare(
                t1.getPriority() != null ? t1.getPriority() : Integer.MAX_VALUE,
                t2.getPriority() != null ? t2.getPriority() : Integer.MAX_VALUE
        );
        // 如果 t1.priority < t2.priority，t1应该排前面，所以这里取反
        if (priorityComparison != 0) {
            return -priorityComparison; 
        }

        // 2. 交期 (越早越紧迫，应该先排)
        LocalDateTime t1Delivery = t1.getOrder() != null ? t1.getOrder().getDeliveryTime() : null;
        LocalDateTime t2Delivery = t2.getOrder() != null ? t2.getOrder().getDeliveryTime() : null;
        
        if (t1Delivery != null && t2Delivery != null) {
            int deliveryComparison = t1Delivery.compareTo(t2Delivery);
            // 如果 t1.deliveryTime < t2.deliveryTime，t1应该排前面，所以取反
            if (deliveryComparison != 0) {
                return -deliveryComparison;
            }
        } else if (t1Delivery != null) {
            return 1;
        } else if (t2Delivery != null) {
            return -1;
        }

        // 3. 按照任务ID作为最后的稳定排序，id一般不为空
        if (t1.getId() != null && t2.getId() != null) {
            return t1.getId().compareTo(t2.getId());
        }
        return 0;
    }
}