package cn.windwheel.aps.solve.config;

import cn.windwheel.aps.solve.engine.ManagedSolverSession;
import cn.windwheel.aps.solve.engine.OptaPlannerEngine;
import cn.windwheel.aps.solve.engine.PlanningSolverEngine;
import cn.windwheel.aps.solve.engine.TimefoldV1Engine;
import cn.windwheel.aps.solve.model.PlanningStrategy;
import cn.windwheel.common.exception.BusinessException;
import cn.windwheel.common.utils.TenantContextHolder;
import org.springframework.stereotype.Component;

import javax.annotation.PreDestroy;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Routes historical profiles to OptaPlanner and TF_* profiles to Timefold, per tenant. */
@Component
public class SolveManageConfigurator {
    private final Map<String, ManagedSolverSession> sessions = new ConcurrentHashMap<>();
    private final List<PlanningSolverEngine> engines = List.of(new OptaPlannerEngine(), new TimefoldV1Engine());

    public ManagedSolverSession initSolverManager(PlanningStrategy strategy) {
        SolverSettings.validate(strategy == null ? null : strategy.getConfig());
        String tenant = tenantId();
        return sessions.compute(tenant, (key, existing) -> {
            if (existing != null) {
                throw new BusinessException("当前租户已有排产任务运行中");
            }
            var profile = SolverSettings.profile(strategy.getConfig());
            return engines.stream().filter(engine -> engine.supports(profile)).findFirst()
                    .orElseThrow(() -> new BusinessException("不支持的排产算法：" + profile))
                    .createSession(strategy.getConfig());
        });
    }

    public ManagedSolverSession getSolverManager() {
        return sessions.get(tenantId());
    }

    public void release(ManagedSolverSession session) {
        if (session != null && sessions.remove(tenantId(), session)) {
            session.close();
        }
    }

    @PreDestroy
    public void close() {
        sessions.values().forEach(ManagedSolverSession::close);
        sessions.clear();
    }

    private String tenantId() {
        String tenant = TenantContextHolder.getTenantId();
        if (tenant == null || tenant.isBlank()) {
            throw new BusinessException("排产请求缺少租户信息");
        }
        return tenant;
    }
}
