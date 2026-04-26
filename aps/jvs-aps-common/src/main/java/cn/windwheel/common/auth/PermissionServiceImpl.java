package cn.windwheel.common.auth;

import cn.hutool.core.util.StrUtil;
import cn.windwheel.common.utils.AuthContextHolder;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * 基于当前请求上下文的轻量权限解析
 */
@Service
public class PermissionServiceImpl implements PermissionService {

    private static final String ROLE_APS_ADMIN = "APS_ADMIN";
    private static final String ROLE_APS_PLAN_MANAGER = "APS_PLAN_MANAGER";
    private static final String ROLE_APS_SCHEDULER = "APS_SCHEDULER";
    private static final String ROLE_APS_DISPATCHER = "APS_DISPATCHER";

    private static final Map<String, Set<String>> ROLE_PERMISSION_MAP;

    static {
        Map<String, Set<String>> rolePermissionMap = new HashMap<>();
        rolePermissionMap.put(ROLE_APS_ADMIN, Set.of("*"));
        rolePermissionMap.put(ROLE_APS_PLAN_MANAGER, Set.of(
                "aps:material:view", "aps:material:edit",
                "aps:production-order:view", "aps:production-order:edit",
                "aps:incoming-material-order:view", "aps:incoming-material-order:edit",
                "aps:bom:view", "aps:bom:edit",
                "aps:process:view", "aps:process:edit",
                "aps:process-route:view", "aps:process-route:edit",
                "aps:work-mode:view", "aps:work-mode:edit",
                "aps:work-calendar:view", "aps:work-calendar:edit",
                "aps:resource:view", "aps:resource:edit",
                "aps:strategy:view", "aps:strategy:edit",
                "aps:schedule:run", "aps:schedule:view", "aps:schedule:preview", "aps:schedule:cancel", "aps:schedule:confirm",
                "aps:schedule-result:view",
                "aps:task-adjust:edit", "aps:task-adjust:view",
                "aps:task-assignment:export",
                "aps:task-report:edit", "aps:task-report:import",
                "aps:report-field:view", "aps:report-field:edit",
                "aps:audit:view"
        ));
        rolePermissionMap.put(ROLE_APS_SCHEDULER, Set.of(
                "aps:material:view",
                "aps:production-order:view",
                "aps:incoming-material-order:view",
                "aps:bom:view",
                "aps:process:view",
                "aps:process-route:view",
                "aps:work-mode:view",
                "aps:work-calendar:view",
                "aps:resource:view",
                "aps:strategy:view",
                "aps:schedule:run", "aps:schedule:view", "aps:schedule:preview", "aps:schedule:cancel",
                "aps:schedule-result:view",
                "aps:task-adjust:edit", "aps:task-adjust:view",
                "aps:task-assignment:export",
                "aps:task-report:edit", "aps:task-report:import",
                "aps:report-field:view"
        ));
        rolePermissionMap.put(ROLE_APS_DISPATCHER, Set.of(
                "aps:material:view",
                "aps:production-order:view",
                "aps:incoming-material-order:view",
                "aps:bom:view",
                "aps:process:view",
                "aps:process-route:view",
                "aps:work-mode:view",
                "aps:work-calendar:view",
                "aps:resource:view",
                "aps:schedule:view",
                "aps:schedule-result:view",
                "aps:task-adjust:view",
                "aps:task-assignment:export",
                "aps:task-report:edit", "aps:task-report:import"
        ));
        ROLE_PERMISSION_MAP = Collections.unmodifiableMap(rolePermissionMap);
    }

    @Override
    public boolean hasPermission(String permission) {
        if (StrUtil.isBlank(permission)) {
            return true;
        }
        Set<String> permissionCodes = getEffectivePermissionCodes();
        if (permissionCodes.isEmpty()) {
            return false;
        }
        return permissionCodes.stream().anyMatch(granted -> matchPermission(granted, permission));
    }

    @Override
    public boolean hasRole(String roleCode) {
        if (StrUtil.isBlank(roleCode)) {
            return false;
        }
        return AuthContextHolder.getRoleCodes().stream().anyMatch(roleCode::equalsIgnoreCase);
    }

    private Set<String> getEffectivePermissionCodes() {
        Set<String> permissionCodes = new LinkedHashSet<>();
        for (String roleCode : AuthContextHolder.getRoleCodes()) {
            if (StrUtil.isBlank(roleCode)) {
                continue;
            }
            permissionCodes.add(roleCode.trim());
            Set<String> builtInPermissions = ROLE_PERMISSION_MAP.get(roleCode.trim());
            if (builtInPermissions != null) {
                permissionCodes.addAll(builtInPermissions);
            }
        }
        return permissionCodes;
    }

    private boolean matchPermission(String grantedPermission, String requiredPermission) {
        if (StrUtil.isBlank(grantedPermission)) {
            return false;
        }
        String granted = grantedPermission.trim();
        if ("*".equals(granted) || requiredPermission.equals(granted)) {
            return true;
        }
        if (!granted.endsWith("*")) {
            return false;
        }
        String prefix = granted.substring(0, granted.length() - 1);
        return requiredPermission.startsWith(prefix);
    }
}
