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
import cn.windwheel.aps.security.ServiceRequestAuthenticator;
import com.alibaba.fastjson2.JSON;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
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

    private static final List<String> PUBLIC_PATHS = List.of(
            "/health",
            "/auth/login",
            "/auth/logout"
    );

    private final ApsAuthService authService;
    private final ApsAuthProperties authProperties;
    private final ObjectProvider<ServiceRequestAuthenticator> serviceAuthenticators;

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
            ServletRequest effectiveRequest = request;
            if (!isValid(authContext)) {
                // 登录会话缺失时才尝试服务账号签名认证，已有的会话身份不会被替换或降级。
                try {
                    ServiceRequestAuthenticator.ServiceAuthentication service = authenticateServiceRequest(request);
                    if (service != null) {
                        authContext = service.context();
                        effectiveRequest = service.request();
                    }
                } catch (ServiceRequestAuthenticator.ServiceAuthException rejected) {
                    writeRejectedResponse(response, rejected.getStatus(), rejected.getMessage());
                    return;
                }
                if (!isValid(authContext)) {
                    writeUnauthorizedResponse(response, "缺少必需的租户或用户上下文");
                    return;
                }
                request.setAttribute(AuthSessionKeys.REQUEST_AUTH_CONTEXT_SOURCE, "service");
            }
            AuthContextHolder.setAuthContext(authContext);
            filterChain.doFilter(effectiveRequest, response);
        } finally {
            AuthContextHolder.clear();
            SystemThreadLocal.clear();
        }
    }

    /**
     * 只在请求没有会话身份时调用。认证器只认领自己声明支持的方法与路径，其余一律放行到“未认证”。
     */
    private ServiceRequestAuthenticator.ServiceAuthentication authenticateServiceRequest(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        for (ServiceRequestAuthenticator authenticator : serviceAuthenticators) {
            if (!authenticator.supports(request.getMethod(), path)) {
                continue;
            }
            ServiceRequestAuthenticator.ServiceAuthentication authentication = authenticator.authenticate(request);
            if (authentication != null) {
                return authentication;
            }
        }
        return null;
    }

    private void writeRejectedResponse(HttpServletResponse response, int status, String message) throws Exception {
        response.setStatus(status);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(JSON.toJSONString(R.failed(status, message)));
        response.getWriter().flush();
    }

    private boolean isPublicPath(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        return PUBLIC_PATHS.contains(path);
    }

    private AuthContext resolveAuthContext(HttpServletRequest request) {
        AuthContext sessionAuthContext = authService.resolveSessionAuthContext(request);
        if (isValid(sessionAuthContext)) {
            request.setAttribute(AuthSessionKeys.REQUEST_AUTH_CONTEXT_SOURCE, "session");
            return sessionAuthContext;
        }
        if (authProperties.isAllowHeaderAuth()) {
            AuthContext headerAuthContext = buildHeaderAuthContext(request);
            if (isValid(headerAuthContext)) {
                request.setAttribute(AuthSessionKeys.REQUEST_AUTH_CONTEXT_SOURCE, "header");
                return headerAuthContext;
            }
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
