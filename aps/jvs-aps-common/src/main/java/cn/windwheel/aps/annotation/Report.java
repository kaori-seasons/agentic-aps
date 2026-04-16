package cn.windwheel.aps.annotation;

import cn.windwheel.aps.entity.enums.ReportTypeEnum;
import org.springframework.stereotype.Service;

import java.lang.annotation.*;

/**
 * @author jvs
 * 报告注解
 */
@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Service
public @interface Report {

    /**
     * 报表类型
     */
    ReportTypeEnum type();

}
