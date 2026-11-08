package cn.windwheel.aps.solve.config;

import ai.timefold.solver.core.config.localsearch.LocalSearchPhaseConfig;
import ai.timefold.solver.core.config.localsearch.decider.forager.FinalistPodiumType;
import cn.windwheel.aps.entity.dto.planning.ConstraintWeightConfigDTO;
import cn.windwheel.aps.entity.dto.planning.StrategyConfigDTO;
import cn.windwheel.aps.entity.enums.PlanningDirectionEnum;
import cn.windwheel.aps.entity.enums.SolverProfileEnum;
import cn.windwheel.aps.solve.engine.OptaPlannerEngine;
import cn.windwheel.aps.solve.engine.TimefoldV1Engine;
import cn.windwheel.common.exception.BusinessException;
import com.alibaba.fastjson.JSON;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.math.BigDecimal;
import static org.junit.jupiter.api.Assertions.*;

class SolverSettingsTest {
    private StrategyConfigDTO config(SolverProfileEnum profile) {
        return new StrategyConfigDTO().setDirection(PlanningDirectionEnum.FORWARD).setSolverProfile(profile);
    }

    @ParameterizedTest
    @EnumSource(SolverProfileEnum.class)
    void exactlyOneEngineOwnsEachPreset(SolverProfileEnum profile) {
        assertNotEquals(new OptaPlannerEngine().supports(profile), new TimefoldV1Engine().supports(profile));
        assertEquals(profile.name().startsWith("TF_"), new TimefoldV1Engine().supports(profile));
    }

    @ParameterizedTest
    @EnumSource(value = SolverProfileEnum.class, names = {"TF_TABU", "TF_LATE_ACCEPTANCE", "TF_SIMULATED_ANNEALING", "TF_GREAT_DELUGE", "TF_TABU_OSCILLATION"})
    void timefoldPresetsBuildTheirOwnAlgorithms(SolverProfileEnum profile) {
        var solver = new TimefoldV1Engine().buildSolverConfig(config(profile).setRandomSeed(123L));
        assertEquals(123L, solver.getRandomSeed());
        assertEquals("NONE", solver.getMoveThreadCount());
        assertEquals(2, solver.getPhaseConfigList().size());
        assertNotEquals(Boolean.TRUE, solver.getTerminationConfig().getBestScoreFeasible());
        var localSearch = (LocalSearchPhaseConfig) solver.getPhaseConfigList().get(1);
        var acceptor = localSearch.getAcceptorConfig();
        assertEquals(SolverSettings.acceptedCount(config(profile)), localSearch.getForagerConfig().getAcceptedCountLimit());
        switch (profile) {
            case TF_TABU -> assertEquals(7, acceptor.getValueTabuSize());
            case TF_LATE_ACCEPTANCE -> assertEquals(400, acceptor.getLateAcceptanceSize());
            case TF_SIMULATED_ANNEALING -> assertEquals("0hard/1000soft", acceptor.getSimulatedAnnealingStartingTemperature());
            case TF_GREAT_DELUGE -> assertEquals(0.000000005, acceptor.getGreatDelugeWaterLevelIncrementRatio());
            case TF_TABU_OSCILLATION -> {
                assertEquals(7, acceptor.getValueTabuSize());
                assertEquals(FinalistPodiumType.STRATEGIC_OSCILLATION, localSearch.getForagerConfig().getFinalistPodiumType());
            }
            default -> fail("Unexpected preset");
        }
    }

    @Test
    void legacyJsonAndNullProfileRemainLegacy() {
        StrategyConfigDTO config = JSON.parseObject("{\"direction\":\"FORWARD\"}", StrategyConfigDTO.class);
        assertEquals(SolverProfileEnum.DEFAULT, SolverSettings.profile(config));
        assertEquals(Boolean.TRUE, new OptaPlannerEngine().buildSolverConfig(config).getTerminationConfig().getBestScoreFeasible());
        config.setSolverProfile(null);
        assertEquals(SolverProfileEnum.DEFAULT, SolverSettings.profile(config));
    }

    @ParameterizedTest
    @EnumSource(SolverProfileEnum.class)
    void advancedSettingsRoundTripInExistingJsonColumn(SolverProfileEnum profile) {
        var original = config(profile).setAcceptedCountLimit(4).setMaxSolveTimeSeconds(25L).setRandomSeed(0L)
                .setGreatDelugeWaterLevelIncrementRatio(new BigDecimal("0.000000005"))
                .setConstraintWeightConfig(new ConstraintWeightConfigDTO().setMinimizeTardinessWeight(83));
        var parsed = JSON.parseObject(JSON.toJSONString(original), StrategyConfigDTO.class);
        assertEquals(original, parsed);
    }

    @Test
    void rejectInvalidActiveParametersAndWeights() {
        assertThrows(BusinessException.class, () -> SolverSettings.validate(config(SolverProfileEnum.TF_TABU).setEntityTabuSize(0)));
        assertThrows(BusinessException.class, () -> SolverSettings.validate(config(SolverProfileEnum.TF_LATE_ACCEPTANCE).setLateAcceptanceSize(-1)));
        assertThrows(BusinessException.class, () -> SolverSettings.validate(config(SolverProfileEnum.TF_TABU).setAcceptedCountLimit(0)));
        assertThrows(BusinessException.class, () -> SolverSettings.validate(config(SolverProfileEnum.DEFAULT).setMaxSolveTimeSeconds(0L)));
        assertThrows(BusinessException.class, () -> SolverSettings.validate(config(SolverProfileEnum.DEFAULT).setMaxSolveTimeSeconds(Long.MAX_VALUE)));
        assertThrows(BusinessException.class, () -> SolverSettings.validate(config(SolverProfileEnum.DEFAULT).setMaxNoImprovementTime(Long.MAX_VALUE)));
        assertThrows(BusinessException.class, () -> SolverSettings.validate(config(SolverProfileEnum.TF_SIMULATED_ANNEALING).setSimulatedAnnealingStartingTemperature("bad")));
        assertThrows(BusinessException.class, () -> SolverSettings.validate(config(SolverProfileEnum.TF_SIMULATED_ANNEALING).setSimulatedAnnealingStartingTemperature("0hard/0soft")));
        assertThrows(BusinessException.class, () -> SolverSettings.validate(config(SolverProfileEnum.TF_GREAT_DELUGE).setGreatDelugeWaterLevelIncrementRatio(BigDecimal.ONE)));
        assertThrows(BusinessException.class, () -> SolverSettings.validate(config(SolverProfileEnum.DEFAULT)
                .setConstraintWeightConfig(new ConstraintWeightConfigDTO().setPriorityWeight(-1))));
        assertDoesNotThrow(() -> SolverSettings.validate(config(SolverProfileEnum.DEFAULT).setSimulatedAnnealingStartingTemperature("inactive")));
    }

    @Test
    void weightsAreBoundToEachFactoryNotToTheCallingThread() {
        var one = new TimefoldV1Engine().buildSolverConfig(config(SolverProfileEnum.TF_TABU)
                .setConstraintWeightConfig(new ConstraintWeightConfigDTO().setPriorityWeight(83)));
        var two = new OptaPlannerEngine().buildSolverConfig(config(SolverProfileEnum.FFD_TABU)
                .setConstraintWeightConfig(new ConstraintWeightConfigDTO().setPriorityWeight(12)));
        assertEquals("83", one.getScoreDirectorFactoryConfig().getConstraintProviderCustomProperties().get("priorityWeight"));
        assertEquals("12", two.getScoreDirectorFactoryConfig().getConstraintProviderCustomProperties().get("priorityWeight"));
    }
}
