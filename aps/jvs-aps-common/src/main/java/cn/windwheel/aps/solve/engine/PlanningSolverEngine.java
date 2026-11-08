package cn.windwheel.aps.solve.engine;

import cn.windwheel.aps.entity.dto.planning.StrategyConfigDTO;
import cn.windwheel.aps.entity.enums.SolverProfileEnum;

/** Vendor managers and jobs stay inside the engine; results retain the existing business format. */
public interface PlanningSolverEngine {
    boolean supports(SolverProfileEnum profile);
    ManagedSolverSession createSession(StrategyConfigDTO config);
}
