package cn.windwheel.aps.shortage;

import java.util.List;

/**
 * 邮件通道。实现放在 mgr：它依赖邮件 starter，而那是可选依赖，common 不应被迫引入。
 */
public interface MailGateway {
    /**
     * @return 失败原因，成功返回 null
     */
    String send(String from, List<String> recipients, String subject, String text);
}
