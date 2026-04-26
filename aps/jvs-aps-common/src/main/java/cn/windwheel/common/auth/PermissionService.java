package cn.windwheel.common.auth;

/**
 * 权限解析服务
 */
public interface PermissionService {

    /**
     * 是否具备指定权限
     *
     * @param permission 权限编码
     * @return true-具备
     */
    boolean hasPermission(String permission);

    /**
     * 是否具备指定角色
     *
     * @param roleCode 角色编码
     * @return true-具备
     */
    boolean hasRole(String roleCode);
}
