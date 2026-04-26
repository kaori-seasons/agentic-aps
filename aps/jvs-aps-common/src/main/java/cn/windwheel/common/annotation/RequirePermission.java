package cn.windwheel.common.annotation;

import java.lang.annotation.*;

/**
 * 接口权限声明
 */
@Documented
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface RequirePermission {

    /**
     * 权限编码
     */
    String value();
}
