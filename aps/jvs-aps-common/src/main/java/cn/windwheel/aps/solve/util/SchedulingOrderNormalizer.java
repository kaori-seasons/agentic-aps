package cn.windwheel.aps.solve.util;

import cn.windwheel.aps.solve.model.ProductionOrder;
import cn.windwheel.aps.solve.model.ProductionTask;
import java.util.*;

/** 冻结任务与新MRP任务可能引用同一业务订单的不同对象，入库前统一引用。 */
public final class SchedulingOrderNormalizer {
    private SchedulingOrderNormalizer() { }

    public static void appendSupplementOrders(List<ProductionOrder> orders, List<ProductionTask> tasks) {
        Map<String, ProductionOrder> canonical = new LinkedHashMap<>();
        // 优先使用新MRP订单的数量与交期；全部冻结时保留历史订单。
        tasks.stream().filter(task -> Boolean.TRUE.equals(task.getSupplement()))
                .sorted(Comparator.comparing(task -> Boolean.TRUE.equals(task.getPinned())))
                .map(ProductionTask::getOrder).filter(Objects::nonNull)
                .forEach(order -> canonical.putIfAbsent(order.getCode(), order));
        tasks.stream().filter(task -> Boolean.TRUE.equals(task.getSupplement()) && task.getOrder() != null)
                .forEach(task -> task.setOrder(canonical.get(task.getOrder().getCode())));
        orders.removeIf(order -> canonical.containsKey(order.getCode()));
        orders.addAll(canonical.values());
    }
}
