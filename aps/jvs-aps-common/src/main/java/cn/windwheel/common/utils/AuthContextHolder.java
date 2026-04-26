package cn.windwheel.common.utils;

import cn.windwheel.common.constant.SysConstant;
import cn.windwheel.common.model.AuthContext;
import lombok.experimental.UtilityClass;

import java.util.Collections;
import java.util.List;

/**
 * 当前线程认证上下文
 */
@UtilityClass
public class AuthContextHolder {

    public void setAuthContext(AuthContext authContext) {
        SystemThreadLocal.set(SysConstant.AUTH_CONTEXT, authContext);
        if (authContext != null) {
            TenantContextHolder.setTenantId(authContext.getTenantId());
        }
    }

    public AuthContext getAuthContext() {
        return SystemThreadLocal.get(SysConstant.AUTH_CONTEXT);
    }

    public String getUserId() {
        AuthContext authContext = getAuthContext();
        return authContext == null ? null : authContext.getUserId();
    }

    public String getTenantId() {
        AuthContext authContext = getAuthContext();
        return authContext == null ? null : authContext.getTenantId();
    }

    public String getUsername() {
        AuthContext authContext = getAuthContext();
        return authContext == null ? null : authContext.getUsername();
    }

    public List<String> getRoleCodes() {
        AuthContext authContext = getAuthContext();
        if (authContext == null || authContext.getRoleCodes() == null) {
            return Collections.emptyList();
        }
        return authContext.getRoleCodes();
    }

    public List<String> getFactoryIds() {
        AuthContext authContext = getAuthContext();
        if (authContext == null || authContext.getFactoryIds() == null) {
            return Collections.emptyList();
        }
        return authContext.getFactoryIds();
    }

    public List<String> getWorkshopIds() {
        AuthContext authContext = getAuthContext();
        if (authContext == null || authContext.getWorkshopIds() == null) {
            return Collections.emptyList();
        }
        return authContext.getWorkshopIds();
    }

    public List<String> getResourceGroupIds() {
        AuthContext authContext = getAuthContext();
        if (authContext == null || authContext.getResourceGroupIds() == null) {
            return Collections.emptyList();
        }
        return authContext.getResourceGroupIds();
    }

    public void clear() {
        SystemThreadLocal.remove(SysConstant.AUTH_CONTEXT);
        TenantContextHolder.clear();
    }
}
