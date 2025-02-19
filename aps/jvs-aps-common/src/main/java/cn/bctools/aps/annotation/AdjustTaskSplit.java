package cn.bctools.aps.annotation;

import cn.bctools.aps.enums.TaskSplitTypeEnum;
import org.springframework.stereotype.Service;

import java.lang.annotation.*;

/**
 * @author jvs
 * 任务拆分方式注解
 */
@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Service
public @interface AdjustTaskSplit {

    /**
     * 任务拆分方式
     */
    TaskSplitTypeEnum type();
}
