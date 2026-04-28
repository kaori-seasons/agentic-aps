package cn.windwheel.aps.config;

import cn.hutool.core.util.StrUtil;
import cn.windwheel.aps.auth.ApsAuthProperties;
import cn.windwheel.aps.auth.ApsAuthService;
import cn.windwheel.aps.auth.AuthSessionKeys;
import cn.windwheel.common.constant.SysConstant;
import cn.windwheel.common.model.AuthContext;
import cn.windwheel.common.utils.AuthContextHolder;
import cn.windwheel.common.utils.R;
import cn.windwheel.common.utils.SystemThreadLocal;
import com.alibaba.fastjson2.JSON;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.GenericFilterBean;

import javax.servlet.FilterChain;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 通用请求过滤器
 * <p>
 * 处理请求版本, 租户id相关的上下文数据
 *
 * @author jvs
 */
@Slf4j
@Component
@RequiredArgsConstructor
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class ContextHolderFilter extends GenericFilterBean {

    private static final List<String> PUBLIC_PATH_SUFFIXES = List.of(
            "/health",
            "/auth/login",
            "/auth/logout"
    );

    private final ApsAuthService authService;
    private final ApsAuthProperties authProperties;

    @Override
    @SneakyThrows
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain) {
        HttpServletRequest request = (HttpServletRequest) servletRequest;
        HttpServletResponse response = (HttpServletResponse) servletResponse;
        if ("OPTIONS".equalsIgnoreCase(request.getMethod()) || isPublicPath(request)) {
            filterChain.doFilter(request, response);
            return;
        }
        try {
            AuthContext authContext = resolveAuthContext(request);
            if (!isValid(authContext)) {
                writeUnauthorizedResponse(response, "缺少必需的租户或用户上下文");
                return;
            }
            AuthContextHolder.setAuthContext(authContext);
            filterChain.doFilter(request, response);
        } finally {
            AuthContextHolder.clear();
            SystemThreadLocal.clear();
        }
    }

    private boolean isPublicPath(HttpServletRequest request) {
        String requestUri = request.getRequestURI();
        return PUBLIC_PATH_SUFFIXES.stream().anyMatch(requestUri::endsWith);
    }

    private AuthContext resolveAuthContext(HttpServletRequest request) {
        AuthContext headerAuthContext = buildHeaderAuthContext(request);
        if (isValid(headerAuthContext)) {
            request.setAttribute(AuthSessionKeys.REQUEST_AUTH_CONTEXT_SOURCE, "header");
            return headerAuthContext;
        }
        AuthContext sessionAuthContext = authService.resolveSessionAuthContext(request);
        if (isValid(sessionAuthContext)) {
            request.setAttribute(AuthSessionKeys.REQUEST_AUTH_CONTEXT_SOURCE, "session");
            return sessionAuthContext;
        }
        AuthContext queryAuthContext = buildQueryAuthContext(request);
        if (isValid(queryAuthContext)) {
            request.setAttribute(AuthSessionKeys.REQUEST_AUTH_CONTEXT_SOURCE, "query");
            return queryAuthContext;
        }
        return null;
    }

    private AuthContext buildHeaderAuthContext(HttpServletRequest request) {
        String tenantId = firstNonBlank(
                request.getHeader(SysConstant.HEADER_TENANT_ID),
                request.getHeader(SysConstant.TENANTID)
        );
        String userId = firstNonBlank(
                request.getHeader(SysConstant.HEADER_USER_ID)
        );
        String username = firstNonBlank(
                request.getHeader(SysConstant.HEADER_USERNAME),
                userId
        );
        List<String> roleCodes = parseList(firstNonBlank(
                request.getHeader(SysConstant.HEADER_ROLE_CODES)
        ));
        List<String> factoryIds = parseList(firstNonBlank(
                request.getHeader(SysConstant.HEADER_FACTORY_IDS)
        ));
        List<String> workshopIds = parseList(firstNonBlank(
                request.getHeader(SysConstant.HEADER_WORKSHOP_IDS)
        ));
        List<String> resourceGroupIds = parseList(firstNonBlank(
                request.getHeader(SysConstant.HEADER_RESOURCE_GROUP_IDS)
        ));
        return new AuthContext()
                .setTenantId(tenantId)
                .setUserId(userId)
                .setUsername(username)
                .setRoleCodes(roleCodes)
                .setFactoryIds(factoryIds)
                .setWorkshopIds(workshopIds)
                .setResourceGroupIds(resourceGroupIds);
    }

    private AuthContext buildQueryAuthContext(HttpServletRequest request) {
        if (!authProperties.isAllowQueryFallback()) {
            return null;
        }
        String tenantId = firstNonBlank(request.getParameter("tenantId"));
        String userId = firstNonBlank(request.getParameter("userId"));
        String username = firstNonBlank(request.getParameter("username"), userId);
        List<String> roleCodes = parseList(firstNonBlank(request.getParameter("roleCodes")));
        List<String> factoryIds = parseList(firstNonBlank(request.getParameter("factoryIds")));
        List<String> workshopIds = parseList(firstNonBlank(request.getParameter("workshopIds")));
        List<String> resourceGroupIds = parseList(firstNonBlank(request.getParameter("resourceGroupIds")));
        return new AuthContext()
                .setTenantId(tenantId)
                .setUserId(userId)
                .setUsername(username)
                .setRoleCodes(roleCodes)
                .setFactoryIds(factoryIds)
                .setWorkshopIds(workshopIds)
                .setResourceGroupIds(resourceGroupIds);
    }

    private boolean isValid(AuthContext authContext) {
        return authContext != null
                && StrUtil.isNotBlank(authContext.getTenantId())
                && StrUtil.isNotBlank(authContext.getUserId())
                && authContext.getRoleCodes() != null
                && !authContext.getRoleCodes().isEmpty();
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (StrUtil.isNotBlank(value)) {
                return value.trim();
            }
        }
        return null;
    }

    private List<String> parseList(String value) {
        if (StrUtil.isBlank(value)) {
            return Collections.emptyList();
        }
        String trimValue = value.trim();
        if (trimValue.startsWith("[") && trimValue.endsWith("]")) {
            try {
                return JSON.parseArray(trimValue, String.class)
                        .stream()
                        .filter(StrUtil::isNotBlank)
                        .map(String::trim)
                        .distinct()
                        .collect(Collectors.toList());
            } catch (Exception e) {
                log.warn("解析上下文列表失败, value={}", trimValue, e);
            }
        }
        return Arrays.stream(trimValue.split(","))
                .map(String::trim)
                .filter(StrUtil::isNotBlank)
                .distinct()
                .collect(Collectors.toList());
    }

    private void writeUnauthorizedResponse(HttpServletResponse response, String message) throws Exception {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(JSON.toJSONString(R.failed(HttpServletResponse.SC_UNAUTHORIZED, message)));
        response.getWriter().flush();
    }

}
