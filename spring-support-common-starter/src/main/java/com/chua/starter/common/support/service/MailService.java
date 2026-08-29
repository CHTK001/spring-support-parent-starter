package com.chua.starter.common.support.service;

import com.chua.common.support.lang.code.ReturnResult;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 邮件发送服务实现
 * <p>
 * 使用 Spring Boot 自动配置的 JavaMailSender，
 * 邮件配置请在 application.properties 中设置 spring.mail.* 属性。
 * </p>
 *
 * @since 2026/8/4
 * @author CH
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MailService {

    /** JavaMailSender Bean（由 Spring Boot 自动配置） */
    private final JavaMailSender mailSender;

    /**
     * 发送简单文本邮件
     *
     * @param to      收件人
     * @param subject 邮件主题
     * @param content 邮件正文
     * @return 结果
     */
    public ReturnResult<Void> sendSimpleMail(String to, String subject, String content) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            // 从 Spring 环境配置获取 from 地址，默认 fallback
            String from = System.getProperty("spring.mail.from", "no-reply@example.com");
            message.setFrom(from);
            message.setTo(to);
            message.setSubject(subject);
            message.setText(content);
            mailSender.send(message);
            log.info("[mail] 简单邮件发送成功: {} -> {}，主题: {}", to, from, subject);
            return ReturnResult.ok();
        } catch (Exception e) {
            log.error("[mail] 简单邮件发送失败: {}", e.getMessage(), e);
            return ReturnResult.error(e.getMessage());
        }
    }

    /**
     * 发送带附件的 HTML 邮件
     *
     * @param to      收件人
     * @param subject 邮件主题
     * @param content 邮件正文（HTML格式）
     * @param files   附件文件路径列表
     * @return 结果
     */
    public ReturnResult<Void> sendHtmlMail(String to, String subject, String content, List<String> files) {
        try {
            MimeMessage mimeMessage = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, StandardCharsets.UTF_8.name());

            // 从 Spring 环境配置获取 from 地址
            String from = System.getProperty("spring.mail.from", "no-reply@example.com");
            helper.setFrom(from);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(content, true); // true表示HTML格式

            // 添加附件
            if (files != null && !files.isEmpty()) {
                for (String filePath : files) {
                    helper.addAttachment(filePath, new java.io.File(filePath));
                }
            }

            mailSender.send(mimeMessage);
            log.info("[mail] HTML邮件发送成功: {} -> {}，主题: {}", to, from, subject);
            return ReturnResult.ok();
        } catch (MessagingException e) {
            log.error("[mail] HTML邮件发送失败: {}", e.getMessage(), e);
            return ReturnResult.error("邮件发送失败: " + e.getMessage());
        }
    }

    /**
     * 发送站内通知邮件（用于告警通知等）
     *
     * @param to      收件人邮箱
     * @param title   邮件标题
     * @param content 邮件正文
     * @return 结果
     */
    public ReturnResult<Void> sendNoticeMail(String to, String title, String content) {
        try {
            MimeMessage mimeMessage = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, StandardCharsets.UTF_8.name());

            String from = System.getProperty("spring.mail.from", "no-reply@example.com");
            helper.setFrom(from);
            helper.setTo(to);
            helper.setSubject(title);
            helper.setText(content, true); // true表示HTML格式

            mailSender.send(mimeMessage);
            log.info("[notice] 站内通知邮件发送成功: {} -> {}，标题: {}", to, from, title);
            return ReturnResult.ok();
        } catch (MessagingException e) {
            log.error("[notice] 站内通知邮件发送失败: {}", e.getMessage(), e);
            return ReturnResult.error("邮件发送失败: " + e.getMessage());
        }
    }
}