package cn.windwheel.aps.mail;

import cn.windwheel.aps.shortage.MailGateway;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;

import javax.mail.MessagingException;
import javax.mail.internet.MimeMessage;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 邮件通道。邮件依赖是可选的，只有打开 aps.shortage.mail.enabled 并配置 spring.mail.* 才会装配。
 * 文案走模板，不做生成式内容。
 */
@Configuration
public class ShortageMailConfig {

    @Bean
    @ConditionalOnProperty(name = "aps.shortage.mail.enabled", havingValue = "true")
    @ConditionalOnClass(JavaMailSenderImpl.class)
    public MailGateway shortageMailGateway(ObjectProvider<JavaMailSender> sender) {
        return new JavaMailSenderMailGateway(sender.getIfAvailable());
    }

    static class JavaMailSenderMailGateway implements MailGateway {
        private final JavaMailSender sender;

        JavaMailSenderMailGateway(JavaMailSender sender) {
            this.sender = sender;
        }

        @Override
        public String send(String from, List<String> recipients, String subject, String text) {
            if (sender == null) {
                return "邮件通道未生效：缺少 spring.mail.* 配置";
            }
            if (from == null || from.isBlank()) {
                return "邮件通道未生效：缺少 aps.shortage.mail.from";
            }
            try {
                MimeMessage message = sender.createMimeMessage();
                MimeMessageHelper helper = new MimeMessageHelper(message, false, StandardCharsets.UTF_8.name());
                helper.setFrom(from);
                helper.setTo(recipients.toArray(String[]::new));
                helper.setSubject(subject);
                helper.setText(text, false);
                sender.send(message);
                return null;
            } catch (MailException | MessagingException e) {
                return "邮件发送失败：" + e.getClass().getSimpleName();
            }
        }
    }
}
