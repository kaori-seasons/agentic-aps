package cn.windwheel.aps.security;

import cn.windwheel.common.model.AuthContext;

import javax.servlet.http.HttpServletRequest;

/**
 * 服务账号类请求的认证器：没有登录会话的机器对机器调用，靠签名而不是靠密码来证明身份。
 * <p>
 * 只在登录会话缺失时才会被咨询，已有的会话身份不会被替换或降级；
 * 每个认证器只认领自己声明支持的方法与路径，其余请求一概不碰。
 */
public interface ServiceRequestAuthenticator {

    boolean supports(String method, String path);

    /**
     * 认证通过返回身份；校验失败抛 {@link ServiceAuthException}，由过滤器按其中的状态码拒绝请求。
     * 返回的 request 是包装过的，正文可被下游再次读取。
     */
    ServiceAuthentication authenticate(HttpServletRequest request);

    record ServiceAuthentication(AuthContext context, HttpServletRequest request) {
    }

    class ServiceAuthException extends RuntimeException {
        private final int status;

        public ServiceAuthException(int status, String message) {
            super(message);
            this.status = status;
        }

        public int getStatus() {
            return status;
        }
    }
}
