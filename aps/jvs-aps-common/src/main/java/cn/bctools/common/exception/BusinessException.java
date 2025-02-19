package cn.bctools.common.exception;


import cn.bctools.common.utils.SpringContextUtil;
import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serializable;

/**
 * @author guojing
 * @describe 运行业务运行时异常
 */
@Data
@Accessors(chain = true)
public class BusinessException extends RuntimeException implements Serializable {

    private static final long serialVersionUID = -7517200941745261512L;

    /**
     * 业务错误code
     * 默认 code为-1 ， 所有的业务失败建议使用-1 ，其它情况如果需要将错误信息返回给前端界面，使用其它 code码，并在网关添加 code码的错误信息转换
     */
    private int code = -1;
    private String message;

    /**
     * 内部使用的异常处理
     *
     * @param message 错误信息
     */
    public BusinessException(String message) {
        super(SpringContextUtil.msg(message, null));
        this.message = super.getMessage();
    }

    /**
     * 内部使用的异常处理,增强,适用于message需要拼接多个参数
     *
     * @param message 错误信息
     * @param params  参数列表
     */
    public BusinessException(String message, Object... params) {
        super(SpringContextUtil.msg(message, params));
        this.message = super.getMessage();
    }

    /**
     * 外部使用，如果出现了外部使用的异常必须使用Code
     *
     * @param message 错误信息
     * @param code    错误code、不同的系统，可以使用不同的code
     */
    public BusinessException(String message, int code) {
        super(SpringContextUtil.msg(message, null));
        this.code = code;
        this.message = super.getMessage();
    }

    /**
     * @param message 错误信息
     * @param code    错误code、不同的系统，可以使用不同的code、系统的异常数据
     */
    public BusinessException(String message, int code, Throwable cause) {
        super(SpringContextUtil.msg(message, null), cause);
        this.code = code;
        this.message = super.getMessage();
    }
}
