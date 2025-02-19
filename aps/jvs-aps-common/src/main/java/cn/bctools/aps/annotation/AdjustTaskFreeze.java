package cn.bctools.aps.annotation;

import cn.bctools.aps.enums.TaskFreezeTypeEnum;
import org.springframework.stereotype.Service;

import java.lang.annotation.*;

/**
 * @author jvs
 * 任务锁定/解锁方式注解
 */
@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Service
public @interface AdjustTaskFreeze {

    /**
     * 锁定/解锁方式
     */
    TaskFreezeTypeEnum type();
}
