package cn.windwheel.common.annotation;

import java.lang.annotation.*;

/**
 * 操作审计声明
 */
@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface AuditOperation {

    /**
     * 操作名称
     */
    String value();
}
