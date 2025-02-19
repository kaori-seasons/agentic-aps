package cn.bctools.aps.annotation;

import cn.bctools.aps.entity.enums.PlanningDirectionEnum;
import org.springframework.stereotype.Service;

import java.lang.annotation.*;

/**
 * @author jvs
 * 排程方向
 */
@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Service
public @interface PlanningDirection {

    /**
     * 排程方向
     */
    PlanningDirectionEnum direction();
}
