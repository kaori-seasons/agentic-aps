package cn.windwheel.aps.security;

import com.alibaba.fastjson2.JSON;
import cn.hutool.core.util.StrUtil;
import cn.windwheel.aps.entity.OperationAuditLogPO;
import cn.windwheel.common.annotation.AuditOperation;
import cn.windwheel.common.annotation.RequirePermission;
import cn.windwheel.common.utils.AuthContextHolder;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import java.lang.reflect.Method;
import java.util.*;

/**
 * 审计日志公共支持
 */
@Slf4j
@Component
public class WebAuditLogSupport {

    public String resolvePermission(Method method, Class<?> targetClass) {
        RequirePermission methodAnnotation = AnnotatedElementUtils.findMergedAnnotation(method, RequirePermission.class);
        if (methodAnnotation != null) {
            return methodAnnotation.value();
        }
        RequirePermission classAnnotation = AnnotatedElementUtils.findMergedAnnotation(targetClass, RequirePermission.class);
        return classAnnotation == null ? null : classAnnotation.value();
    }

    public String resolveOperationName(Method method, Class<?> targetClass) {
        AuditOperation methodAnnotation = AnnotatedElementUtils.findMergedAnnotation(method, AuditOperation.class);
        if (methodAnnotation != null && StrUtil.isNotBlank(methodAnnotation.value())) {
            return methodAnnotation.value();
        }
        RequirePermission permission = AnnotatedElementUtils.findMergedAnnotation(method, RequirePermission.class);
        if (permission == null) {
            permission = AnnotatedElementUtils.findMergedAnnotation(targetClass, RequirePermission.class);
        }
        if (permission != null && StrUtil.isNotBlank(permission.value())) {
            return permission.value();
        }
        return method.getName();
    }

    public OperationAuditLogPO buildAuditLog(String operationName, String permissionCode, JoinPoint joinPoint) {
        HttpServletRequest request = currentRequest();
        return new OperationAuditLogPO()
                .setTenantId(AuthContextHolder.getTenantId())
                .setUserId(AuthContextHolder.getUserId())
                .setUsername(AuthContextHolder.getUsername())
                .setRoleCodes(JSON.toJSONString(AuthContextHolder.getRoleCodes()))
                .setOperationName(operationName)
                .setPermissionCode(permissionCode)
                .setHttpMethod(request == null ? null : request.getMethod())
                .setRequestUri(request == null ? null : request.getRequestURI())
                .setClientIp(resolveClientIp(request))
                .setRequestParams(summarizeArgs(joinPoint == null ? null : joinPoint.getArgs()));
    }

    private HttpServletRequest currentRequest() {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        return attributes == null ? null : attributes.getRequest();
    }

    private String resolveClientIp(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (StrUtil.isNotBlank(forwardedFor)) {
            return forwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private String summarizeArgs(Object[] args) {
        if (args == null || args.length == 0) {
            return null;
        }
        List<Object> summaries = new ArrayList<>();
        for (Object arg : args) {
            if (arg == null || arg instanceof ServletRequest || arg instanceof ServletResponse) {
                continue;
            }
            if (arg instanceof MultipartFile file) {
                Map<String, Object> fileSummary = new LinkedHashMap<>();
                fileSummary.put("fileName", file.getOriginalFilename());
                fileSummary.put("size", file.getSize());
                summaries.add(fileSummary);
                continue;
            }
            summaries.add(arg);
        }
        if (summaries.isEmpty()) {
            return null;
        }
        try {
            return JSON.toJSONString(summaries);
        } catch (Exception e) {
            log.warn("序列化审计请求参数失败", e);
            return "[unserializable]";
        }
    }
}
