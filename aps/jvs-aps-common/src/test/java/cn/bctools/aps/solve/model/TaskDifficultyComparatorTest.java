package cn.bctools.aps.solve.model;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

public class TaskDifficultyComparatorTest {

    @Test
    public void testCompareByPriority() {
        TaskDifficultyComparator comparator = new TaskDifficultyComparator();
        
        ProductionTask task1 = new ProductionTask().setId("1").setPriority(1); // 最高优先级
        ProductionTask task2 = new ProductionTask().setId("2").setPriority(5);
        
        // FFD 排序需要 ascending 顺序（最难的在最后）。所以更难的应该被认为是 "更大"的。
        // task1 优先级 1，task2 优先级 5。task1 比 task2 难，所以 task1 应该 "大于" task2，compare(task1, task2) 返回 > 0。
        Assertions.assertTrue(comparator.compare(task1, task2) > 0);
        Assertions.assertTrue(comparator.compare(task2, task1) < 0);
    }

    @Test
    public void testCompareByDeliveryTime() {
        TaskDifficultyComparator comparator = new TaskDifficultyComparator();
        
        ProductionOrder order1 = new ProductionOrder();
        order1.setDeliveryTime(LocalDateTime.of(2023, 10, 1, 12, 0));
        ProductionTask task1 = new ProductionTask().setId("1").setPriority(1).setOrder(order1);
        
        ProductionOrder order2 = new ProductionOrder();
        order2.setDeliveryTime(LocalDateTime.of(2023, 10, 5, 12, 0));
        ProductionTask task2 = new ProductionTask().setId("2").setPriority(1).setOrder(order2);
        
        // task1 交期早，更难，应该 "大于" task2，compare(task1, task2) 返回 > 0
        Assertions.assertTrue(comparator.compare(task1, task2) > 0);
        Assertions.assertTrue(comparator.compare(task2, task1) < 0);
    }
}