package cn.windwheel.aps.solve.config;

import cn.windwheel.aps.entity.dto.planning.StrategyConfigDTO;
import cn.windwheel.aps.entity.enums.PlanningDirectionEnum;
import cn.windwheel.aps.entity.enums.SolverProfileEnum;
import cn.windwheel.aps.solve.engine.ManagedSolverSession;
import cn.windwheel.aps.solve.model.PlanningStrategy;
import cn.windwheel.common.exception.BusinessException;
import cn.windwheel.common.utils.TenantContextHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.junit.jupiter.api.Assertions.*;

class SolveManageConfiguratorTest {
    private final SolveManageConfigurator configurator = new SolveManageConfigurator();

    @AfterEach
    void tearDown() {
        configurator.close();
        TenantContextHolder.clear();
    }

    @ParameterizedTest
    @EnumSource(SolverProfileEnum.class)
    void everyPresetCreatesARealSolverManager(SolverProfileEnum profile) {
        TenantContextHolder.setTenantId("tenant-" + profile);
        ManagedSolverSession session = configurator.initSolverManager(strategy(profile));
        assertSame(session, configurator.getSolverManager());
        configurator.release(session);
        assertNull(configurator.getSolverManager());
    }

    @Test
    void oldAndNewEnginesCoexistAcrossTenants() {
        TenantContextHolder.setTenantId("legacy");
        ManagedSolverSession legacy = configurator.initSolverManager(strategy(SolverProfileEnum.FFD_TABU));
        TenantContextHolder.setTenantId("timefold");
        ManagedSolverSession timefold = configurator.initSolverManager(strategy(SolverProfileEnum.TF_TABU));
        assertNotSame(legacy, timefold);
        assertSame(timefold, configurator.getSolverManager());
        configurator.release(timefold);
        TenantContextHolder.setTenantId("legacy");
        assertSame(legacy, configurator.getSolverManager());
    }

    @Test
    void aSecondSolveCannotCloseAnActiveSession() {
        TenantContextHolder.setTenantId("same-tenant");
        ManagedSolverSession active = configurator.initSolverManager(strategy(SolverProfileEnum.DEFAULT));
        assertThrows(BusinessException.class,
                () -> configurator.initSolverManager(strategy(SolverProfileEnum.TF_TABU)));
        assertSame(active, configurator.getSolverManager());
    }

    @Test
    void missingProfileStillCreatesLegacySession() {
        TenantContextHolder.setTenantId("old-json");
        assertNotNull(configurator.initSolverManager(strategy(null)));
    }

    private PlanningStrategy strategy(SolverProfileEnum profile) {
        PlanningStrategy strategy = new PlanningStrategy();
        strategy.setConfig(new StrategyConfigDTO().setDirection(PlanningDirectionEnum.FORWARD)
                .setSolverProfile(profile).setRandomSeed(1L));
        return strategy;
    }
}
