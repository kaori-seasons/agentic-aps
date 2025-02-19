package cn.bctools.common.constant;

import cn.bctools.common.utils.SpringContextUtil;

/**
 * 系统常量
 * <p>
 * 包含请求头名称, RedisKey等
 *
 * @author Administrator
 */
public class SysConstant {

    public static final String TENANTID = "jvs-tenantId";

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
