package cn.windwheel.aps.shortage;

/** 缺料预警的拒绝类错误。code 是对外返回的 HTTP 语义码，由控制器映射成统一响应。 */
public class ShortageException extends RuntimeException {
    private final int code;

    public ShortageException(int code, String message) {
        super(message);
        this.code = code;
    }

    public static ShortageException missing(String what) {
        return new ShortageException(404, what + "不存在");
    }

    public static ShortageException conflict(String message) {
        return new ShortageException(409, message);
    }

    public int getCode() {
        return code;
    }
}
