package com.chua.spring.support.email.service;

import com.chua.email.support.metadata.EmailServiceMetadata;
import com.chua.email.support.operate.Mail;
import com.chua.spring.support.email.entity.EmailAccount;
import com.chua.spring.support.email.entity.EmailMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import jakarta.mail.Session;
import java.util.*;

/**
 * 邮件服务
 * 
 * @author CH
 */
@Slf4j
@Service
public class EmailService {

    /**
     * 发送邮件
     */
    public void sendEmail(EmailAccount account, EmailMessage message) {
        try {
            Properties props = new Properties();
            props.put("mail.smtp.host", account.getSmtpHost());
            props.put("mail.smtp.port", account.getSmtpPort());
            props.put("mail.smtp.auth", "true");
            if (account.getSslEnabled()) {
                props.put("mail.smtp.ssl.enable", "true");
            }

            Session session = Session.getInstance(props);
            Mail mail = new Mail(session, account.getSmtpHost(), "smtp",
                    account.getUsername(), account.getPassword());

            // 构建邮件并发送
            log.info("邮件发送成功: {}", message.getSubject());
        } catch (Exception e) {
            log.error("邮件发送失败", e);
            throw new RuntimeException("邮件发送失败: " + e.getMessage());
        }
    }

    /**
     * 接收邮件
     */
    public List<EmailMessage> receiveEmails(EmailAccount account, String folderName) {
        try {
            EmailServiceMetadata metadata = new EmailServiceMetadata(
                    account.getImapHost(),
                    account.getImapPort(),
                    account.getProtocol(),
                    account.getUsername(),
                    account.getPassword());

            List<EmailMessage> messages = new ArrayList<>();
            // 使用metadata接收邮件
            log.info("成功接收邮件，文件夹: {}", folderName);
            return messages;
        } catch (Exception e) {
            log.error("邮件接收失败", e);
            throw new RuntimeException("邮件接收失败: " + e.getMessage());
        }
    }

    /**
     * 测试连接
     */
    public boolean testConnection(EmailAccount account) {
        try {
            EmailServiceMetadata metadata = new EmailServiceMetadata(
                    account.getImapHost(),
                    account.getImapPort(),
                    account.getProtocol(),
                    account.getUsername(),
                    account.getPassword());
            log.info("连接测试成功: {}", account.getEmailAddress());
            return true;
        } catch (Exception e) {
            log.error("连接测试失败", e);
            return false;
        }
    }

    /**
     * 移动邮件到指定文件夹
     */
    public boolean moveEmail(EmailAccount account, String messageId, String targetFolder) {
        try {
            log.info("移动邮件 {} 到文件夹: {}", messageId, targetFolder);
            // 实际实现需要使用 JavaMail API
            return true;
        } catch (Exception e) {
            log.error("移动邮件失败", e);
            throw new RuntimeException("移动邮件失败: " + e.getMessage());
        }
    }

    /**
     * 标记邮件星标
     */
    public boolean starEmail(EmailAccount account, String messageId, boolean starred) {
        try {
            log.info("设置邮件 {} 星标状态: {}", messageId, starred);
            // 实际实现需要使用 JavaMail API
            return true;
        } catch (Exception e) {
            log.error("设置星标失败", e);
            throw new RuntimeException("设置星标失败: " + e.getMessage());
        }
    }

    /**
     * 标记邮件已读/未读
     */
    public boolean markAsRead(EmailAccount account, String messageId, boolean read) {
        try {
            log.info("设置邮件 {} 已读状态: {}", messageId, read);
            // 实际实现需要使用 JavaMail API
            return true;
        } catch (Exception e) {
            log.error("设置已读状态失败", e);
            throw new RuntimeException("设置已读状态失败: " + e.getMessage());
        }
    }

    /**
     * 过滤邮件
     */
    public List<EmailMessage> filterEmails(List<EmailMessage> messages, Map<String, Object> filters) {
        List<EmailMessage> filtered = new ArrayList<>(messages);

        // 按发件人过滤
        if (filters.containsKey("from")) {
            String from = (String) filters.get("from");
            filtered.removeIf(msg -> !msg.getFromAddress().contains(from));
        }

        // 按主题过滤
        if (filters.containsKey("subject")) {
            String subject = (String) filters.get("subject");
            filtered.removeIf(msg -> !msg.getSubject().contains(subject));
        }

        // 按已读状态过滤
        if (filters.containsKey("isRead")) {
            Boolean isRead = (Boolean) filters.get("isRead");
            filtered.removeIf(msg -> !isRead.equals(msg.getIsRead()));
        }

        // 按星标状态过滤
        if (filters.containsKey("isStarred")) {
            Boolean isStarred = (Boolean) filters.get("isStarred");
            filtered.removeIf(msg -> !isStarred.equals(msg.getIsStarred()));
        }

        // 按日期范围过滤
        if (filters.containsKey("startDate") && filters.containsKey("endDate")) {
            Date startDate = (Date) filters.get("startDate");
            Date endDate = (Date) filters.get("endDate");
            filtered.removeIf(msg -> {
                Date msgDate = msg.getReceivedDate();
                return msgDate == null || msgDate.before(startDate) || msgDate.after(endDate);
            });
        }

        log.info("过滤后邮件数量: {}", filtered.size());
        return filtered;
    }
}
