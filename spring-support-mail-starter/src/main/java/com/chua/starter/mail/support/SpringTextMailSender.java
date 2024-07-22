package com.chua.starter.mail.support;

import com.chua.common.support.annotations.Spi;
import com.chua.common.support.lang.mail.*;
import com.chua.common.support.log.Log;
import com.chua.common.support.utils.UrlUtils;
import org.springframework.core.io.InputStreamSource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;

import jakarta.annotation.Resource;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.List;

/**
 * spring
 * @author CH
 */
@Spi(value = "text", order = 1)
public class SpringTextMailSender extends AbstractMailSender {

    private static final Log log = Log.getLogger(MailSender.class);
    @Autowired
    private JavaMailSender javaMailSender;

    public SpringTextMailSender(MailConfiguration configuration) {
        super(configuration);
    }

    @Override
    public void send(String from, Mail mail) throws Exception {
        MimeMessage message = javaMailSender.createMimeMessage();
        try {
            MimeMessageHelper helper = new MimeMessageHelper(message, true);
            addAttach(helper, from, mail);
            helper.setFrom(from);
            helper.setTo(mail.getTo());
            helper.setSubject(mail.getTitle());
            helper.setText(mail.getContent());
            javaMailSender.send(message);
            log.info("纯文本的邮件已经发送给【{}】。", mail.getTo());
        } catch (Exception e) {
            log.error("纯文本邮件发送时发生异常！", e);
        }
    }


    protected void addAttach(MimeMessageHelper helper, String from, Mail mail) {
        if(!mail.hasAttach()) {
            return;
        }
        List<EmailAttachment> attachment1 = mail.getAttachment();
        for (EmailAttachment emailAttachment : attachment1) {
            URL url = emailAttachment.getUrl();
            try {
                helper.addAttachment(UrlUtils.getFileName(url.openConnection()), new InputStreamSource() {
                    @Override
                    public InputStream getInputStream() throws IOException {
                        return url.openStream();
                    }
                });
            } catch (MessagingException ignored) {
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }
}
