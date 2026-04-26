package cn.windwheel.aps.security;

import cn.windwheel.aps.entity.OperationAuditLogPO;
import cn.windwheel.aps.service.OperationAuditLogService;
import cn.windwheel.common.auth.PermissionService;
import cn.windwheel.common.exception.BusinessException;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;

/**
 * 接口权限切面
 */
@Slf4j
@Aspect
@Component
@AllArgsConstructor
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class PermissionAspect {

    private final PermissionService permissionService;
    private final OperationAuditLogService operationAuditLogService;
    private final WebAuditLogSupport webAuditLogSupport;

    @Before("@within(cn.windwheel.common.annotation.RequirePermission) || @annotation(cn.windwheel.common.annotation.RequirePermission)")
    public void checkPermission(JoinPoint joinPoint) {
        Method method = ((MethodSignature) joinPoint.getSignature()).getMethod();
        Class<?> targetClass = joinPoint.getTarget().getClass();
        String permission = webAuditLogSupport.resolvePermission(method, targetClass);
        if (permissionService.hasPermission(permission)) {
            return;
        }

        OperationAuditLogPO auditLog = webAuditLogSupport.buildAuditLog(
                webAuditLogSupport.resolveOperationName(method, targetClass),
                permission,
                joinPoint
        ).setSuccess(false)
                .setErrorMessage("无权限访问")
                .setDurationMs(0L);
        saveQuietly(auditLog);
        throw new BusinessException("无权限访问", 403);
    }

    private void saveQuietly(OperationAuditLogPO auditLog) {
        try {
            operationAuditLogService.saveAuditLog(auditLog);
        } catch (Exception e) {
            log.error("保存权限拒绝审计日志失败", e);
        }
    }
}
