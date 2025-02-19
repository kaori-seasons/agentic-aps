package cn.bctools.database.config;

import cn.bctools.common.utils.function.Get;
import cn.bctools.database.entity.po.BasalPo;
import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * @author jvs
 */
@Component
public class AutoFillConfig implements MetaObjectHandler {

    @Override
    public void insertFill(MetaObject metaObject) {
        LocalDateTime now = LocalDateTime.now();
        this.strictInsertFill(metaObject, Get.name(BasalPo::getCreateTime), () -> now, LocalDateTime.class);
        this.strictInsertFill(metaObject, Get.name(BasalPo::getUpdateTime), () -> now, LocalDateTime.class);
        this.strictInsertFill(metaObject, Get.name(BasalPo::getCreateBy), () -> "jvs", String.class);
        this.strictInsertFill(metaObject, Get.name(BasalPo::getUpdateBy), () -> "jvs", String.class);
        this.strictInsertFill(metaObject, Get.name(BasalPo::getCreateById), () -> "1", String.class);
    }

    @Override
    public void updateFill(MetaObject metaObject) {
        metaObject.setValue(Get.name(BasalPo::getUpdateTime), LocalDateTime.now());
        String updateByFiledName = Get.name(BasalPo::getUpdateBy);
        if (metaObject.hasSetter(updateByFiledName)) {
            metaObject.setValue(updateByFiledName, null);
            this.strictUpdateFill(metaObject, updateByFiledName, () -> "jvs", String.class);
        }
    }
}
