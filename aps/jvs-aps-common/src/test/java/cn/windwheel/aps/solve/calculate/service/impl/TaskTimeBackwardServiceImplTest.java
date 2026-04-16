package cn.windwheel.aps.solve.calculate.service.impl;

import cn.windwheel.aps.entity.dto.ProcessRouteNodePropertiesDTO;
import cn.windwheel.aps.entity.dto.ProcessUseMainResourcesDTO;
import cn.windwheel.aps.entity.enums.ProcessRelationshipEnum;
import cn.windwheel.aps.solve.model.MainProductionResource;
import cn.windwheel.aps.solve.model.ProductionTask;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TaskTimeBackwardServiceImplTest {

    @Test
    void eeDualWhenFrontTaskShorterShouldBeLimitedByStartBound() throws Exception {
        // front: 1h, next: 2h, buffer: 30m -> latestEnd = min(11:30, 10:30) = 10:30
        ProductionTask frontTask = createTask("R1", BigDecimal.ONE, "1Hp", ProcessRelationshipEnum.ES, null, null, null);
        ProductionTask nextTask = createTask("R1", new BigDecimal("2"), "1Hp", ProcessRelationshipEnum.EE, null, null, "30M");
        nextTask.setStartTime(LocalDateTime.of(2026, 1, 1, 10, 0));
        nextTask.setEndTime(LocalDateTime.of(2026, 1, 1, 12, 0));

        LocalDateTime latestEnd = invokeCalculateLatestEndTimeByNextTask(frontTask, nextTask);

        assertEquals(LocalDateTime.of(2026, 1, 1, 10, 30), latestEnd);
    }

    @Test
    void eeDualWhenFrontTaskLongerShouldBeLimitedByEndBound() throws Exception {
        // front: 3h, next: 2h, buffer: 30m -> latestEnd = min(11:30, 12:00) = 11:30
        ProductionTask frontTask = createTask("R1", new BigDecimal("3"), "1Hp", ProcessRelationshipEnum.ES, null, null, null);
        ProductionTask nextTask = createTask("R1", new BigDecimal("2"), "1Hp", ProcessRelationshipEnum.EE, null, null, "30M");
        nextTask.setStartTime(LocalDateTime.of(2026, 1, 1, 10, 0));
        nextTask.setEndTime(LocalDateTime.of(2026, 1, 1, 12, 0));

        LocalDateTime latestEnd = invokeCalculateLatestEndTimeByNextTask(frontTask, nextTask);

        assertEquals(LocalDateTime.of(2026, 1, 1, 11, 30), latestEnd);
    }

    private LocalDateTime invokeCalculateLatestEndTimeByNextTask(ProductionTask frontTask, ProductionTask nextTask) throws Exception {
        TaskTimeBackwardServiceImpl service = new TaskTimeBackwardServiceImpl();
        Method method = TaskTimeBackwardServiceImpl.class.getDeclaredMethod("calculateLatestEndTimeByNextTask", ProductionTask.class, ProductionTask.class);
        method.setAccessible(true);
        return (LocalDateTime) method.invoke(service, frontTask, nextTask);
    }

    private ProductionTask createTask(String resourceId,
                                      BigDecimal quantity,
                                      String throughput,
                                      ProcessRelationshipEnum processRelationship,
                                      String preInterval,
                                      String postInterval,
                                      String bufferTime) {
        ProcessUseMainResourcesDTO useMainResource = new ProcessUseMainResourcesDTO()
                .setId(resourceId)
                .setThroughput(throughput);
        ProcessRouteNodePropertiesDTO process = new ProcessRouteNodePropertiesDTO()
                .setCode("P1")
                .setProcessRelationship(processRelationship)
                .setPreIntervalDuration(preInterval)
                .setPostIntervalDuration(postInterval)
                .setBufferTime(bufferTime)
                .setUseMainResources(List.of(useMainResource));
        MainProductionResource resource = new MainProductionResource()
                .setId(resourceId);
        return new ProductionTask()
                .setId("T-" + quantity)
                .setCode("TASK-" + quantity)
                .setProcess(process)
                .setResource(resource)
                .setQuantity(quantity)
                .setPinned(false)
                .setMergeTaskCode(null);
    }
}
