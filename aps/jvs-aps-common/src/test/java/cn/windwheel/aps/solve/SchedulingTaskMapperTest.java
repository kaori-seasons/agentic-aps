package cn.windwheel.aps.solve;

import cn.windwheel.aps.entity.PlanTaskPendingPO;
import cn.windwheel.aps.solve.model.MainProductionResource;
import cn.windwheel.aps.solve.util.SchedulingTaskMapper;
import cn.windwheel.common.utils.BeanCopyUtil;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class SchedulingTaskMapperTest {
    @Test
    void convertsCyclicSolvedGraphWithoutSerializingResourceQueues() {
        var task = HybridSchedulingTest.task("TASK");
        var resource = new MainProductionResource().setId("R").setWorkCalendars(List.of());
        task.setResource(resource).setStartTime(HybridSchedulingTest.START);
        resource.setTaskList(new ArrayList<>(List.of(task)));
        var dto = SchedulingTaskMapper.toDTO(task);
        var pending = BeanCopyUtil.copy(dto,PlanTaskPendingPO.class);
        assertEquals("TASK",pending.getCode());
        assertEquals("R",pending.getMainResourceId());
        assertEquals(HybridSchedulingTest.START.plusHours(1),pending.getEndTime());
        assertFalse(pending.getPinned());
        assertSame(task,resource.getTaskList().get(0));
    }
}
