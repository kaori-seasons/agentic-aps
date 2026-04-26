package cn.windwheel.aps.security;

import cn.windwheel.aps.entity.OperationAuditLogPO;
import cn.windwheel.aps.service.OperationAuditLogService;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;

/**
 * 操作审计切面
 */
@Slf4j
@Aspect
@Component
@AllArgsConstructor
@Order(Ordered.LOWEST_PRECEDENCE - 20)
public class AuditOperationAspect {

    private final OperationAuditLogService operationAuditLogService;
    private final WebAuditLogSupport webAuditLogSupport;

    @Around("@annotation(cn.windwheel.common.annotation.AuditOperation)")
    public Object auditOperation(ProceedingJoinPoint joinPoint) throws Throwable {
        Method method = ((MethodSignature) joinPoint.getSignature()).getMethod();
        Class<?> targetClass = joinPoint.getTarget().getClass();
        String permission = webAuditLogSupport.resolvePermission(method, targetClass);
        String operationName = webAuditLogSupport.resolveOperationName(method, targetClass);
        long begin = System.currentTimeMillis();
        try {
            Object result = joinPoint.proceed();
            saveQuietly(webAuditLogSupport.buildAuditLog(operationName, permission, joinPoint)
                    .setSuccess(true)
                    .setDurationMs(System.currentTimeMillis() - begin));
            return result;
        } catch (Throwable throwable) {
            saveQuietly(webAuditLogSupport.buildAuditLog(operationName, permission, joinPoint)
                    .setSuccess(false)
                    .setErrorMessage(throwable.getMessage())
                    .setDurationMs(System.currentTimeMillis() - begin));
            throw throwable;
        }
    }

    private void saveQuietly(OperationAuditLogPO auditLog) {
        try {
            operationAuditLogService.saveAuditLog(auditLog);
        } catch (Exception e) {
            log.error("保存操作审计日志失败", e);
        }
    }
}
