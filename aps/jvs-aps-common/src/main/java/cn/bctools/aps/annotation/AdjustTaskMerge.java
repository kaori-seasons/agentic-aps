package cn.bctools.aps.annotation;

import cn.bctools.aps.enums.TaskMergeTypeEnum;
import org.springframework.stereotype.Service;

import java.lang.annotation.*;

/**
 * @author jvs
 * 任务合并方式注解
 */

@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Service
public @interface AdjustTaskMerge {

    /**
     * 合并任务
     */
    TaskMergeTypeEnum type();
}
