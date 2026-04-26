package cn.windwheel.aps.solve.config;

import cn.windwheel.aps.entity.dto.planning.StrategyConfigDTO;
import cn.windwheel.aps.entity.enums.PlanningDirectionEnum;
import cn.windwheel.aps.entity.enums.SolverProfileEnum;
import cn.windwheel.aps.solve.model.PlanningStrategy;
import cn.windwheel.common.utils.TenantContextHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.optaplanner.core.api.solver.SolverManager;

import java.util.UUID;

class SolveManageConfiguratorTest {

    private final SolveManageConfigurator configurator = new SolveManageConfigurator();

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void initSolverManager_allowsFfdTabuProfileWithPlanningListVariable() {
        TenantContextHolder.setTenantId("test-tenant-ffd-tabu");
        PlanningStrategy strategy = buildStrategy(SolverProfileEnum.FFD_TABU);

        SolverManager<?, UUID> solverManager = configurator.initSolverManager(strategy);

        Assertions.assertNotNull(solverManager);
    }

    @Test
    void initSolverManager_allowsFfdLateAcceptanceProfileWithPlanningListVariable() {
        TenantContextHolder.setTenantId("test-tenant-ffd-late-acceptance");
        PlanningStrategy strategy = buildStrategy(SolverProfileEnum.FFD_LATE_ACCEPTANCE);

        SolverManager<?, UUID> solverManager = configurator.initSolverManager(strategy);

        Assertions.assertNotNull(solverManager);
    }

    private PlanningStrategy buildStrategy(SolverProfileEnum solverProfile) {
        StrategyConfigDTO config = new StrategyConfigDTO()
                .setDirection(PlanningDirectionEnum.FORWARD)
                .setMaterialConstrained(Boolean.TRUE)
                .setSolverProfile(solverProfile)
                .setRandomSeed(1L);

        PlanningStrategy strategy = new PlanningStrategy();
        strategy.setConfig(config);
        return strategy;
    }
}
