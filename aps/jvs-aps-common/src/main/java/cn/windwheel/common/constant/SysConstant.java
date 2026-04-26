package cn.windwheel.common.constant;

import cn.windwheel.common.utils.SpringContextUtil;

/**
 * 系统常量
 * <p>
 * 包含请求头名称, RedisKey等
 *
 * @author Administrator
 */
public class SysConstant {

    public static final String TENANTID = "jvs-tenantId";
    public static final String AUTH_CONTEXT = "jvs-auth-context";

    public static final String HEADER_TENANT_ID = "X-Tenant-Id";
    public static final String HEADER_USER_ID = "X-User-Id";
    public static final String HEADER_USERNAME = "X-Username";
    public static final String HEADER_ROLE_CODES = "X-Role-Codes";
    public static final String HEADER_FACTORY_IDS = "X-Factory-Ids";
    public static final String HEADER_WORKSHOP_IDS = "X-Workshop-Ids";
    public static final String HEADER_RESOURCE_GROUP_IDS = "X-Resource-Group-Ids";

     /**
     * 统一规范化Redis的Key命名，保证所有的Key都可以便于管理
     *
     * @param module 功能模块简称
     * @param key    Redis的key
     * @return
     */
    public static synchronized String redisKey(String module, String key) {
        StringBuffer stringBuffer = new StringBuffer(SpringContextUtil.getApplicationContextName());
        stringBuffer.append(":");
        stringBuffer.append(module);
        stringBuffer.append(":");
        stringBuffer.append(key);
        return stringBuffer.toString().replaceAll("-", "");
    }


}
