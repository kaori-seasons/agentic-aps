package cn.bctools.aps.annotation;

import cn.bctools.aps.enums.TaskMoveTypeEnum;
import org.springframework.stereotype.Service;

import java.lang.annotation.*;

/**
 * @author jvs
 * 移动任务方式注解
 */

@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Service
public @interface AdjustTaskMove {
    /**
     * 移动任务
     */
    TaskMoveTypeEnum type();
}
