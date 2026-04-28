package cn.windwheel.aps.auth;

import cn.hutool.core.util.StrUtil;
import cn.windwheel.aps.dto.SaveUserPreferenceDTO;
import cn.windwheel.aps.entity.UserPreferencePO;
import cn.windwheel.aps.service.UserPreferenceService;
import cn.windwheel.common.exception.BusinessException;
import cn.windwheel.common.model.AuthContext;
import cn.windwheel.common.utils.AuthContextHolder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class ApsAuthService {

    private static final Set<String> SUPPORTED_THEMES = Set.of("darkblue", "simple", "darkred");
    private static final Set<String> SUPPORTED_LANGUAGES = Set.of("zh_CN", "en");
    private static final Map<String, Set<String>> ROLE_PERMISSION_MAP = buildRolePermissionMap();

    private final ApsAuthProperties authProperties;
    private final UserPreferenceService userPreferenceService;

    public Map<String, Object> login(LoginRequest request, HttpSession session) {
        ApsAuthProperties.DemoUser matchedUser = authProperties.getUsers().stream()
                .filter(user -> matches(user, request))
                .findFirst()
                .orElseThrow(() -> new BusinessException("用户名或密码错误", 401));
        AuthContext authContext = toAuthContext(matchedUser);
        session.setAttribute(AuthSessionKeys.SESSION_AUTH_CONTEXT, authContext);
        session.setAttribute(AuthSessionKeys.SESSION_AUTH_PROFILE, toLoginProfile(matchedUser));
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("tenantId", authContext.getTenantId());
        payload.put("userId", authContext.getUserId());
        payload.put("username", authContext.getUsername());
        payload.put("displayName", getDisplayName(authContext, matchedUser));
        payload.put("roleCodes", authContext.getRoleCodes());
        return payload;
    }

    public void logout(HttpSession session) {
        if (session != null) {
            session.invalidate();
        }
    }

    public Map<String, Object> getCurrentUser(HttpServletRequest request) {
        AuthContext authContext = requireCurrentAuthContext();
        ApsAuthProperties.DemoUser configuredUser = findConfiguredUser(authContext).orElse(null);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("tenantId", authContext.getTenantId());
        payload.put("userId", authContext.getUserId());
        payload.put("username", authContext.getUsername());
        payload.put("displayName", getDisplayName(authContext, configuredUser));
        payload.put("roleCodes", authContext.getRoleCodes());
        payload.put("factoryIds", defaultList(authContext.getFactoryIds()));
        payload.put("workshopIds", defaultList(authContext.getWorkshopIds()));
        payload.put("resourceGroupIds", defaultList(authContext.getResourceGroupIds()));
        payload.put("permissions", listPermissions(authContext.getRoleCodes()));
        payload.put("authSource", request.getAttribute(AuthSessionKeys.REQUEST_AUTH_CONTEXT_SOURCE));
        return payload;
    }

    public Map<String, Object> getProfile(HttpServletRequest request) {
        AuthContext authContext = requireCurrentAuthContext();
        ApsAuthProperties.DemoUser configuredUser = findConfiguredUser(authContext).orElse(null);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("tenantId", authContext.getTenantId());
        payload.put("userId", authContext.getUserId());
        payload.put("username", authContext.getUsername());
        payload.put("displayName", getDisplayName(authContext, configuredUser));
        payload.put("roleCodes", defaultList(authContext.getRoleCodes()));
        payload.put("email", configuredUser != null ? configuredUser.getDisplayName() + "@aps.local" : "");
        payload.put("authSource", request.getAttribute(AuthSessionKeys.REQUEST_AUTH_CONTEXT_SOURCE));
        return payload;
    }

    public Map<String, Object> updateProfile(UpdateProfileRequest request, HttpServletRequest httpRequest) {
        AuthContext authContext = requireCurrentAuthContext();
        ApsAuthProperties.DemoUser configuredUser = findConfiguredUser(authContext).orElse(null);
        if (configuredUser != null) {
            configuredUser.setDisplayName(StrUtil.trim(request.getDisplayName()));
        }
        HttpSession session = httpRequest.getSession(false);
        if (session != null) {
            LoginProfile profile = (LoginProfile) session.getAttribute(AuthSessionKeys.SESSION_AUTH_PROFILE);
            if (profile != null) {
                profile.setDisplayName(StrUtil.trim(request.getDisplayName()));
                session.setAttribute(AuthSessionKeys.SESSION_AUTH_PROFILE, profile);
            }
        }
        return getProfile(httpRequest);
    }

    public void changePassword(ChangePasswordRequest request, HttpSession session) {
        AuthContext authContext = requireCurrentAuthContext();
        ApsAuthProperties.DemoUser configuredUser = findConfiguredUser(authContext).orElse(null);
        if (configuredUser == null) {
            throw new BusinessException("当前用户不支持修改密码", 400);
        }
        if (!StrUtil.equals(configuredUser.getPassword(), request.getOldPassword())) {
            throw new BusinessException("旧密码不正确", 400);
        }
        configuredUser.setPassword(StrUtil.trim(request.getNewPassword()));
    }

    public Map<String, Object> getCurrentTenant() {
        AuthContext authContext = requireCurrentAuthContext();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("id", authContext.getTenantId());
        payload.put("systemName", authProperties.getSystemName());
        return payload;
    }

    public Map<String, Object> buildBootstrap(HttpServletRequest request, HttpSession session) {
        AuthContext authContext = requireCurrentAuthContext();
        ApsAuthProperties.DemoUser configuredUser = findConfiguredUser(authContext).orElse(null);
        Map<String, Object> payload = new LinkedHashMap<>();

        Map<String, Object> tenant = new LinkedHashMap<>();
        tenant.put("id", authContext.getTenantId());
        tenant.put("systemName", authProperties.getSystemName());
        payload.put("tenant", tenant);

        Map<String, Object> user = new LinkedHashMap<>();
        user.put("tenantId", authContext.getTenantId());
        user.put("userId", authContext.getUserId());
        user.put("username", authContext.getUsername());
        user.put("displayName", getDisplayName(authContext, configuredUser));
        payload.put("user", user);

        payload.put("roles", defaultList(authContext.getRoleCodes()));
        payload.put("permissions", listPermissions(authContext.getRoleCodes()));

        Map<String, Object> scopes = new LinkedHashMap<>();
        scopes.put("factoryIds", defaultList(authContext.getFactoryIds()));
        scopes.put("workshopIds", defaultList(authContext.getWorkshopIds()));
        scopes.put("resourceGroupIds", defaultList(authContext.getResourceGroupIds()));
        payload.put("scopes", scopes);

        payload.put("preferences", getCurrentPreference(authContext, configuredUser, session));

        payload.put("menus", buildMenuTree());

        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("authSource", request.getAttribute(AuthSessionKeys.REQUEST_AUTH_CONTEXT_SOURCE));
        meta.put("timestamp", new Date());
        payload.put("meta", meta);
        return payload;
    }

    public List<Map<String, Object>> buildMenuTree() {
        List<String> permissions = listPermissions(requireCurrentAuthContext().getRoleCodes());
        List<Map<String, Object>> groups = new ArrayList<>();

        groups.add(buildMenuGroup("基础数据", "jvs-aps-icon-jichushuju", "dataBase", List.of(
                buildMenuItem("/jvs-aps-ui/matter", "物料", "aps:material:view", "matter"),
                buildMenuItem("/jvs-aps-ui/resource", "主资源", "aps:resource:view", "resource"),
                buildMenuItem("/jvs-aps-ui/productionOrder", "生产订单", "aps:production-order:view", "productionOrder"),
                buildMenuItem("/jvs-aps-ui/materialPlan", "来料计划", "aps:incoming-material-order:view", "materialPlan"),
                buildMenuItem("/jvs-aps-ui/bom", "制造BOM", "aps:bom:view", "bom")
        ), permissions));

        groups.add(buildMenuGroup("生产工艺", "jvs-aps-icon-shengchangongyi", "productionEngineering", List.of(
                buildMenuItem("/jvs-aps-ui/technology", "工序模板", "aps:process:view", "technology"),
                buildMenuItem("/jvs-aps-ui/routing", "工艺路线", "aps:process-route:view", "routing"),
                buildMenuItem("/jvs-aps-ui/productionCalendar", "生产日历", "aps:work-calendar:view", "productionCalendar")
        ), permissions));

        groups.add(buildMenuGroup("生产计划", "jvs-aps-icon-shengchanjihua", "programOfProduction", List.of(
                buildMenuItem("/jvs-aps-ui/productionSchedulingStrategy", "排产策略", "aps:strategy:view", "productionSchedulingStrategy"),
                buildMenuItem("/jvs-aps-ui/productionSchedulingPlan", "排产计划", "aps:schedule:view", "productionSchedulingPlan")
        ), permissions));

        groups.add(buildMenuGroup("系统治理", "jvs-aps-icon-jichushuju", "systemGovernance", List.of(
                buildMenuItem("/jvs-aps-ui/operationAudit", "操作审计", "aps:audit:view", "operationAudit")
        ), permissions));

        return groups.stream().filter(g -> {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> children = (List<Map<String, Object>>) g.get("children");
            return children != null && !children.isEmpty();
        }).toList();
    }

    private Map<String, Object> buildMenuGroup(String name, String icon, String langKey, List<Map<String, Object>> children, List<String> permissions) {
        Map<String, Object> group = new LinkedHashMap<>();
        group.put("name", name);
        group.put("icon", icon);
        group.put("langKey", langKey);
        group.put("children", children.stream()
                .filter(c -> {
                    String permFlag = (String) c.get("permisionFlag");
                    return StrUtil.isBlank(permFlag) || permissions.contains("*") || permissions.stream().anyMatch(p -> matchMenuPermission(p, permFlag));
                })
                .toList());
        return group;
    }

    private Map<String, Object> buildMenuItem(String url, String name, String permisionFlag, String langKey) {
        Map<String, Object> extend = new LinkedHashMap<>();
        extend.put("newWindow", false);
        extend.put("icon", "");
        extend.put("url", url);
        extend.put("name", name);

        Map<String, Object> item = new LinkedHashMap<>();
        item.put("extend", extend);
        item.put("name", name);
        item.put("permisionFlag", permisionFlag);
        item.put("langKey", langKey);
        return item;
    }

    private boolean matchMenuPermission(String granted, String required) {
        if (StrUtil.isBlank(granted) || StrUtil.isBlank(required)) {
            return false;
        }
        if (StrUtil.equals(granted, required)) {
            return true;
        }
        if (granted.endsWith(":*")) {
            String prefix = granted.substring(0, granted.length() - 1);
            return required.startsWith(prefix);
        }
        return false;
    }

    public Map<String, Object> getCurrentPreference(HttpSession session) {
        AuthContext authContext = requireCurrentAuthContext();
        ApsAuthProperties.DemoUser configuredUser = findConfiguredUser(authContext).orElse(null);
        return getCurrentPreference(authContext, configuredUser, session);
    }

    public Map<String, Object> saveCurrentPreference(SaveUserPreferenceDTO request, HttpSession session) {
        AuthContext authContext = requireCurrentAuthContext();
        validatePreference(request);
        Map<String, Object> payload = toPreferencePayload(
                authContext.getTenantId(),
                authContext.getUserId(),
                request.getTheme(),
                request.getLanguage(),
                request.getDefaultRoute()
        );
        try {
            UserPreferencePO saved = userPreferenceService.saveOrUpdate(authContext.getTenantId(), authContext.getUserId(), request);
            payload = toPreferencePayload(saved.getTenantId(), saved.getUserId(), saved.getTheme(), saved.getLanguage(), saved.getDefaultRoute());
        } catch (Exception e) {
            log.warn("保存用户偏好到数据库失败，已回退到 session 保存, tenantId={}, userId={}", authContext.getTenantId(), authContext.getUserId(), e);
        }
        session.setAttribute(AuthSessionKeys.SESSION_USER_PREFERENCE, payload);
        return payload;
    }

    @SuppressWarnings("unchecked")
    public AuthContext resolveSessionAuthContext(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null) {
            return null;
        }
        Object authContext = session.getAttribute(AuthSessionKeys.SESSION_AUTH_CONTEXT);
        return authContext instanceof AuthContext ? (AuthContext) authContext : null;
    }

    private Map<String, Object> getCurrentPreference(AuthContext authContext, ApsAuthProperties.DemoUser configuredUser, HttpSession session) {
        try {
            UserPreferencePO saved = userPreferenceService.getByTenantAndUser(authContext.getTenantId(), authContext.getUserId());
            if (saved != null) {
                return toPreferencePayload(saved.getTenantId(), saved.getUserId(), saved.getTheme(), saved.getLanguage(), saved.getDefaultRoute());
            }
        } catch (Exception e) {
            log.warn("读取用户偏好失败，回退到 session/default, tenantId={}, userId={}", authContext.getTenantId(), authContext.getUserId(), e);
        }
        Object sessionPreference = session.getAttribute(AuthSessionKeys.SESSION_USER_PREFERENCE);
        if (sessionPreference instanceof Map<?, ?> preferenceMap) {
            return new LinkedHashMap<>((Map<String, Object>) preferenceMap);
        }
        return toPreferencePayload(
                authContext.getTenantId(),
                authContext.getUserId(),
                configuredUser == null ? authProperties.getDefaultTheme() : defaultString(configuredUser.getTheme(), authProperties.getDefaultTheme()),
                configuredUser == null ? authProperties.getDefaultLanguage() : defaultString(configuredUser.getLanguage(), authProperties.getDefaultLanguage()),
                configuredUser == null ? authProperties.getDefaultRoute() : defaultString(configuredUser.getDefaultRoute(), authProperties.getDefaultRoute())
        );
    }

    private boolean matches(ApsAuthProperties.DemoUser user, LoginRequest request) {
        if (!StrUtil.equals(user.getUsername(), StrUtil.trim(request.getUsername()))) {
            return false;
        }
        if (!StrUtil.equals(user.getPassword(), request.getPassword())) {
            return false;
        }
        return StrUtil.isBlank(request.getTenantId()) || StrUtil.equals(user.getTenantId(), StrUtil.trim(request.getTenantId()));
    }

    private AuthContext toAuthContext(ApsAuthProperties.DemoUser user) {
        return new AuthContext()
                .setTenantId(user.getTenantId())
                .setUserId(defaultString(user.getUserId(), user.getUsername()))
                .setUsername(defaultString(user.getUsername(), user.getUserId()))
                .setRoleCodes(defaultList(user.getRoleCodes()))
                .setFactoryIds(defaultList(user.getFactoryIds()))
                .setWorkshopIds(defaultList(user.getWorkshopIds()))
                .setResourceGroupIds(defaultList(user.getResourceGroupIds()));
    }

    private LoginProfile toLoginProfile(ApsAuthProperties.DemoUser user) {
        return new LoginProfile()
                .setTenantId(user.getTenantId())
                .setUserId(defaultString(user.getUserId(), user.getUsername()))
                .setUsername(defaultString(user.getUsername(), user.getUserId()))
                .setDisplayName(defaultString(user.getDisplayName(), defaultString(user.getUsername(), user.getUserId())));
    }

    private AuthContext requireCurrentAuthContext() {
        AuthContext authContext = AuthContextHolder.getAuthContext();
        if (authContext == null || StrUtil.isBlank(authContext.getTenantId()) || StrUtil.isBlank(authContext.getUserId())) {
            throw new BusinessException("当前未登录", 401);
        }
        return authContext;
    }

    private Optional<ApsAuthProperties.DemoUser> findConfiguredUser(AuthContext authContext) {
        return authProperties.getUsers().stream()
                .filter(user -> StrUtil.equals(user.getTenantId(), authContext.getTenantId()))
                .filter(user -> StrUtil.equals(defaultString(user.getUserId(), user.getUsername()), authContext.getUserId())
                        || StrUtil.equals(user.getUsername(), authContext.getUsername()))
                .findFirst();
    }

    private String getDisplayName(AuthContext authContext, ApsAuthProperties.DemoUser configuredUser) {
        if (configuredUser != null && StrUtil.isNotBlank(configuredUser.getDisplayName())) {
            return configuredUser.getDisplayName().trim();
        }
        return defaultString(authContext.getUsername(), authContext.getUserId());
    }

    private List<String> listPermissions(List<String> roleCodes) {
        Set<String> permissions = new LinkedHashSet<>();
        for (String roleCode : defaultList(roleCodes)) {
            permissions.add(roleCode);
            permissions.addAll(ROLE_PERMISSION_MAP.getOrDefault(roleCode, Collections.emptySet()));
        }
        return new ArrayList<>(permissions);
    }

    private void validatePreference(SaveUserPreferenceDTO request) {
        if (!SUPPORTED_THEMES.contains(request.getTheme())) {
            throw new BusinessException("不支持的主题", request.getTheme());
        }
        if (!SUPPORTED_LANGUAGES.contains(request.getLanguage())) {
            throw new BusinessException("不支持的语言", request.getLanguage());
        }
        if (!request.getDefaultRoute().startsWith("/")) {
            throw new BusinessException("默认首页必须以 / 开头");
        }
    }

    private Map<String, Object> toPreferencePayload(String tenantId, String userId, String theme, String language, String defaultRoute) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("tenantId", tenantId);
        payload.put("userId", userId);
        payload.put("theme", defaultString(theme, authProperties.getDefaultTheme()));
        payload.put("language", defaultString(language, authProperties.getDefaultLanguage()));
        payload.put("defaultRoute", defaultString(defaultRoute, authProperties.getDefaultRoute()));
        return payload;
    }

    private String defaultString(String value, String fallback) {
        return StrUtil.isBlank(value) ? fallback : value.trim();
    }

    private List<String> defaultList(List<String> values) {
        return values == null ? Collections.emptyList() : values.stream()
                .filter(StrUtil::isNotBlank)
                .map(String::trim)
                .distinct()
                .toList();
    }

    private static Map<String, Set<String>> buildRolePermissionMap() {
        Map<String, Set<String>> rolePermissionMap = new LinkedHashMap<>();
        rolePermissionMap.put("APS_ADMIN", Set.of("*"));
        rolePermissionMap.put("APS_PLAN_MANAGER", Set.of(
                "aps:material:view", "aps:material:edit",
                "aps:resource:view", "aps:resource:edit",
                "aps:production-order:view", "aps:production-order:edit",
                "aps:incoming-material-order:view", "aps:incoming-material-order:edit",
                "aps:bom:view", "aps:bom:edit",
                "aps:process:view", "aps:process:edit",
                "aps:process-route:view", "aps:process-route:edit",
                "aps:work-mode:view", "aps:work-mode:edit",
                "aps:work-calendar:view", "aps:work-calendar:edit",
                "aps:strategy:view", "aps:strategy:edit",
                "aps:schedule:run", "aps:schedule:view", "aps:schedule:preview", "aps:schedule:cancel", "aps:schedule:confirm",
                "aps:schedule-result:view",
                "aps:task-adjust:edit", "aps:task-adjust:view",
                "aps:task-assignment:export",
                "aps:task-report:edit", "aps:task-report:import",
                "aps:report-field:view", "aps:report-field:edit",
                "aps:audit:view"
        ));
        rolePermissionMap.put("APS_SCHEDULER", Set.of(
                "aps:material:view",
                "aps:resource:view",
                "aps:production-order:view",
                "aps:incoming-material-order:view",
                "aps:bom:view",
                "aps:process:view",
                "aps:process-route:view",
                "aps:work-mode:view",
                "aps:work-calendar:view",
                "aps:strategy:view",
                "aps:schedule:run", "aps:schedule:view", "aps:schedule:preview", "aps:schedule:cancel",
                "aps:schedule-result:view",
                "aps:task-adjust:edit", "aps:task-adjust:view",
                "aps:task-assignment:export",
                "aps:task-report:edit", "aps:task-report:import",
                "aps:report-field:view"
        ));
        rolePermissionMap.put("APS_DISPATCHER", Set.of(
                "aps:material:view",
                "aps:resource:view",
                "aps:production-order:view",
                "aps:incoming-material-order:view",
                "aps:bom:view",
                "aps:process:view",
                "aps:process-route:view",
                "aps:work-mode:view",
                "aps:work-calendar:view",
                "aps:schedule:view",
                "aps:schedule-result:view",
                "aps:task-adjust:view",
                "aps:task-assignment:export",
                "aps:task-report:edit", "aps:task-report:import"
        ));
        return Collections.unmodifiableMap(rolePermissionMap);
    }
}
