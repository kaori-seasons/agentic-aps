package cn.windwheel.aps.solve.score;

import ai.timefold.solver.test.api.score.stream.ConstraintVerifier;
import cn.windwheel.aps.entity.dto.ProcessRouteNodePropertiesDTO;
import cn.windwheel.aps.entity.dto.ProcessUseMainResourcesDTO;
import cn.windwheel.aps.entity.enums.ProcessRelationshipEnum;
import cn.windwheel.aps.solve.model.MainProductionResource;
import cn.windwheel.aps.solve.model.ProductionTask;
import cn.windwheel.aps.solve.model.SchedulingSolution;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

class TimefoldConstraintProviderTest {
    private final ConstraintVerifier<TimefoldConstraintProvider, SchedulingSolution> verifier = ConstraintVerifier.build(
            new TimefoldConstraintProvider(), SchedulingSolution.class, MainProductionResource.class, ProductionTask.class);
    private static final LocalDateTime START = LocalDateTime.of(2026, 1, 5, 8, 0);

    @Test
    void unassignedTasksArePenalizedButMergedChildrenAreNot() {
        ProductionTask unassigned = task("T1", null);
        ProductionTask merged = task("CHILD", null).setMergeTaskCode("PARENT");
        verifier.verifyThat(TimefoldConstraintProvider::assignedAndTimed)
                .given(unassigned, merged).penalizesBy(1);
    }

    @Test
    void incompatibleResourceIsHardViolation() {
        MainProductionResource resource = new MainProductionResource().setId("WRONG");
        verifier.verifyThat(TimefoldConstraintProvider::matchingResource)
                .given(task("T1", resource)).penalizesBy(1);
    }

    @Test
    void overlappingPairIsCountedOnceWithLongPrecision() {
        MainProductionResource resource = new MainProductionResource().setId("R1");
        ProductionTask first = task("T1", resource).setStartTime(START);
        ProductionTask second = task("T2", resource).setStartTime(START.plusMinutes(30));
        verifier.verifyThat(TimefoldConstraintProvider::noOverlap)
                .given(first, second).penalizesBy(1_800_000L);
    }

    @Test
    void adjoiningTasksDoNotOverlap() {
        MainProductionResource resource = new MainProductionResource().setId("R1");
        verifier.verifyThat(TimefoldConstraintProvider::noOverlap)
                .given(task("T1", resource).setStartTime(START), task("T2", resource).setStartTime(START.plusHours(1)))
                .penalizesBy(0);
    }

    @Test
    void customWeightIsNotReadFromThreadLocal() {
        TimefoldConstraintProvider provider = new TimefoldConstraintProvider();
        provider.setPriorityWeight(83);
        var customVerifier = ConstraintVerifier.build(provider, SchedulingSolution.class,
                MainProductionResource.class, ProductionTask.class);
        MainProductionResource resource = new MainProductionResource().setId("R1");
        customVerifier.verifyThat(TimefoldConstraintProvider::arrangeByPriority)
                .given(task("HIGH", resource).setPriority(1).setStartTime(START.plusHours(1)),
                        task("LOW", resource).setPriority(2).setStartTime(START))
                .penalizesBy(83);
    }

    private static ProductionTask task(String id, MainProductionResource resource) {
        return new ProductionTask().setId(id).setCode(id).setPriority(1).setPinned(false).setResource(resource)
                .setQuantity(BigDecimal.ONE).setProcess(new ProcessRouteNodePropertiesDTO().setCode("P1")
                        .setProcessRelationship(ProcessRelationshipEnum.ES)
                        .setUseMainResources(List.of(new ProcessUseMainResourcesDTO().setId("R1").setThroughput("1Hp"))));
    }
}
