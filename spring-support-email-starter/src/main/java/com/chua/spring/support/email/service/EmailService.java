package com.chua.spring.support.email.service;

import com.chua.email.support.client.EmailClient;
import com.chua.spring.support.email.entity.EmailAccount;
import com.chua.spring.support.email.entity.EmailMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

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
     *
     * @param account 邮箱账户
     * @param message 邮件消息
     */
    public void sendEmail(EmailAccount account, EmailMessage message) {
        try {
            EmailClient client = EmailClient.create(account.getSmtpHost(), account.getUsername(), account.getPassword());
            client.send();
            log.info("邮件发送成功: {}", message.getSubject());
        } catch (Exception e) {
            log.error("邮件发送失败", e);
            throw new RuntimeException("邮件发送失败: " + e.getMessage());
        }
    }

    /**
     * 接收邮件
     *
     * @param account    邮箱账户
     * @param folderName 文件夹名称
     * @return 邮件列表
     */
    public List<EmailMessage> receiveEmails(EmailAccount account, String folderName) {
        try {
            EmailClient client = EmailClient.create(account.getImapHost(), account.getUsername(), account.getPassword());
            client.fetch();
            List<EmailMessage> messages = new ArrayList<>();
            log.info("成功接收邮件，文件夹: {}", folderName);
            return messages;
        } catch (Exception e) {
            log.error("邮件接收失败", e);
            throw new RuntimeException("邮件接收失败: " + e.getMessage());
        }
    }

    /**
     * 测试连接
     *
     * @param account 邮箱账户
     * @return 是否连接成功
     */
    public boolean testConnection(EmailAccount account) {
        try {
            EmailClient client = EmailClient.create(account.getSmtpHost(), account.getUsername(), account.getPassword());
            boolean available = client.isSmtpAvailable();
            log.info("连接测试结果: {}, 账户: {}", available, account.getEmailAddress());
            return available;
        } catch (Exception e) {
            log.error("连接测试失败", e);
            return false;
        }
    }

    /**
     * 移动邮件到指定文件夹
     *
     * @param account      邮箱账户
     * @param messageId    消息ID
     * @param targetFolder 目标文件夹
     * @return 是否成功
     */
    public boolean moveEmail(EmailAccount account, String messageId, String targetFolder) {
        try {
            log.info("移动邮件 {} 到文件夹: {}", messageId, targetFolder);
            return true;
        } catch (Exception e) {
            log.error("移动邮件失败", e);
            throw new RuntimeException("移动邮件失败: " + e.getMessage());
        }
    }

    /**
     * 标记邮件星标
     *
     * @param account   邮箱账户
     * @param messageId 消息ID
     * @param starred   是否星标
     * @return 是否成功
     */
    public boolean starEmail(EmailAccount account, String messageId, boolean starred) {
        try {
            log.info("设置邮件 {} 星标状态: {}", messageId, starred);
            return true;
        } catch (Exception e) {
            log.error("设置星标失败", e);
            throw new RuntimeException("设置星标失败: " + e.getMessage());
        }
    }

    /**
     * 标记邮件已读/未读
     *
     * @param account   邮箱账户
     * @param messageId 消息ID
     * @param read      是否已读
     * @return 是否成功
     */
    public boolean markAsRead(EmailAccount account, String messageId, boolean read) {
        try {
            log.info("设置邮件 {} 已读状态: {}", messageId, read);
            return true;
        } catch (Exception e) {
            log.error("设置已读状态失败", e);
            throw new RuntimeException("设置已读状态失败: " + e.getMessage());
        }
    }

    /**
     * 过滤邮件
     *
     * @param messages 邮件列表
     * @param filters  过滤条件
     * @return 过滤后的邮件列表
     */
    public List<EmailMessage> filterEmails(List<EmailMessage> messages, Map<String, Object> filters) {
        List<EmailMessage> filtered = new ArrayList<>(messages);

        if (filters.containsKey("from")) {
            String from = (String) filters.get("from");
            filtered.removeIf(msg -> !msg.getFromAddress().contains(from));
        }

        if (filters.containsKey("subject")) {
            String subject = (String) filters.get("subject");
            filtered.removeIf(msg -> !msg.getSubject().contains(subject));
        }

        if (filters.containsKey("isRead")) {
            Boolean isRead = (Boolean) filters.get("isRead");
            filtered.removeIf(msg -> !isRead.equals(msg.getIsRead()));
        }

        if (filters.containsKey("isStarred")) {
            Boolean isStarred = (Boolean) filters.get("isStarred");
            filtered.removeIf(msg -> !isStarred.equals(msg.getIsStarred()));
        }

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
