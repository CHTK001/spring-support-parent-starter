package com.chua.starter.email.support;

import com.chua.common.support.annotations.Spi;
import com.chua.advanced.support.lang.mail.*;
import com.chua.common.support.objects.annotation.AutoInject;
import com.chua.common.support.utils.UrlUtils;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;

import java.io.IOException;
import java.net.URL;
import java.util.List;

/**
 * spring
 * @author CH
 */
@Slf4j
@Spi(value = "text", order = 1)
public class SpringTextMailSender extends AbstractMailSender {

    @AutoInject
    private JavaMailSender javaMailSender;

    public SpringTextMailSender(MailConfiguration configuration) {
        super(configuration);
    }

    @Override
    public void send(String from, Email email) throws Exception {
        MimeMessage message = javaMailSender.createMimeMessage();
        try {
            MimeMessageHelper helper = new MimeMessageHelper(message, true);
            addAttach(helper, email);
            helper.setFrom(from);
            helper.setTo(email.getTo());
            helper.setSubject(email.getTitle());
            helper.setText(email.getContent());
            javaMailSender.send(message);
            log.info("纯文本的邮件已经发送给【{}】。", email.getTo());
        } catch (Exception e) {
            log.error("纯文本邮件发送时发生异常！", e);
        }
    }


    protected void addAttach(MimeMessageHelper helper, Email email) {
        if(!email.hasAttach()) {
            return;
        }
        List<EmailAttachment> attachment1 = email.getAttachment();
        for (EmailAttachment emailAttachment : attachment1) {
            URL url = emailAttachment.getUrl();
            try {
                helper.addAttachment(UrlUtils.getFileName(url.openConnection()), url::openStream);
            } catch (MessagingException ignored) {
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }
}
