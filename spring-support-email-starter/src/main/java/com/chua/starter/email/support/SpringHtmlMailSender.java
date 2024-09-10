package com.chua.starter.email.support;

import com.chua.common.support.annotations.Spi;
import com.chua.common.support.lang.mail.Email;
import com.chua.common.support.lang.mail.MailConfiguration;
import com.chua.common.support.lang.mail.MailSender;
import com.chua.common.support.log.Log;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;

/**
 * spring
 * @author CH
 */
@Spi(value = "html", order = 1)
public class SpringHtmlMailSender extends SpringTextMailSender {

    private static final Log log = Log.getLogger(MailSender.class);
    private final JavaMailSenderImpl javaMailSender;
    public SpringHtmlMailSender(MailConfiguration configuration) {
        super(configuration);
        this.javaMailSender = new JavaMailSenderImpl();
        javaMailSender.setHost(configuration.getSmtpHost());
        javaMailSender.setPort(configuration.getSmtpPort());
        javaMailSender.setPassword(configuration.getPassword());
        javaMailSender.setUsername(configuration.getUsername());
        javaMailSender.setDefaultEncoding("UTF-8");
    }

    @Override
    public void send(String from, Email email) throws Exception {
        MimeMessage message = javaMailSender.createMimeMessage();
        try {
            //true表示需要创建一个multipart message
            MimeMessageHelper helper = new MimeMessageHelper(message, true);
            addAttach(helper, email);
            helper.setFrom(from);
            helper.setTo(email.getTo());
            helper.setSubject(email.getTitle());
            helper.setText(email.getContent(), true);
            javaMailSender.send(message);
            log.info("html邮件已经发送{}。", email.getTo());
        } catch (MessagingException e) {
            log.error("发送html邮件时发生异常！", e);
        }
    }
}
