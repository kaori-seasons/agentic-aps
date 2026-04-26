package cn.windwheel.aps.config;

import com.alibaba.fastjson2.JSON;
import cn.hutool.core.util.StrUtil;
import cn.windwheel.common.constant.SysConstant;
import cn.windwheel.common.model.AuthContext;
import cn.windwheel.common.utils.AuthContextHolder;
import cn.windwheel.common.utils.SystemThreadLocal;
import cn.windwheel.common.utils.R;
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
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class ContextHolderFilter extends GenericFilterBean {

    @Override
    @SneakyThrows
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain) {
        HttpServletRequest request = (HttpServletRequest) servletRequest;
        HttpServletResponse response = (HttpServletResponse) servletResponse;
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            filterChain.doFilter(request, response);
            return;
        }
        try {
            AuthContext authContext = buildAuthContext(request);
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

    private AuthContext buildAuthContext(HttpServletRequest request) {
        String tenantId = firstNonBlank(
                request.getHeader(SysConstant.HEADER_TENANT_ID),
                request.getHeader(SysConstant.TENANTID),
                request.getParameter("tenantId")
        );
        String userId = firstNonBlank(
                request.getHeader(SysConstant.HEADER_USER_ID),
                request.getParameter("userId")
        );
        String username = firstNonBlank(
                request.getHeader(SysConstant.HEADER_USERNAME),
                request.getParameter("username"),
                userId
        );
        List<String> roleCodes = parseList(firstNonBlank(
                request.getHeader(SysConstant.HEADER_ROLE_CODES),
                request.getParameter("roleCodes")
        ));
        List<String> factoryIds = parseList(firstNonBlank(
                request.getHeader(SysConstant.HEADER_FACTORY_IDS),
                request.getParameter("factoryIds")
        ));
        List<String> workshopIds = parseList(firstNonBlank(
                request.getHeader(SysConstant.HEADER_WORKSHOP_IDS),
                request.getParameter("workshopIds")
        ));
        List<String> resourceGroupIds = parseList(firstNonBlank(
                request.getHeader(SysConstant.HEADER_RESOURCE_GROUP_IDS),
                request.getParameter("resourceGroupIds")
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
