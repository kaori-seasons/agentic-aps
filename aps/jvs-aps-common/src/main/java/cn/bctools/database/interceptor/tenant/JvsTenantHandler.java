package cn.bctools.database.interceptor.tenant;

import cn.bctools.common.utils.TenantContextHolder;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.baomidou.mybatisplus.extension.plugins.handler.TenantLineHandler;
import lombok.extern.slf4j.Slf4j;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.NullValue;
import net.sf.jsqlparser.expression.StringValue;

/**
 * @author guojing
 */
@Slf4j
public class JvsTenantHandler implements TenantLineHandler {

    private static final String TENANT_ID = "tenant_id";


    /**
     * 获取租户 ID 值表达式，只支持单个 ID 值
     * <p>
     *
     * @return 租户 ID 值表达式
     */
    public Expression getTenantId() {
        String tenantId = TenantContextHolder.getTenantId();
        if (StrUtil.isBlank(tenantId)) {
            log.error("获取租户为空:请检查SQL数据>> ");
            return new NullValue();
        }
        return new StringValue(tenantId);
    }

    /**
     * 根据表名判断是否忽略拼接多租户条件
     * <p>
     * 默认都要进行解析并拼接多租户条件
     *
     * @param tableName 表名
     * @return 是否忽略, true:表示忽略，false:需要解析并拼接多租户条件
     */
    public boolean ignoreTable(String tableName) {
        tableName = tableName.replace("`", "");
        String tenantId = TenantContextHolder.getTenantId();
        if (StringUtils.isBlank(tenantId)) {
            log.debug("当前线程中没有租户id, 跳过租户拦截");
            return true;
        }
        return false;
    }

}
