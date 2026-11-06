package com.chua.starter.collect.notify.impl;

import com.chua.common.support.task.message.MessageResponse;
import com.chua.starter.collect.dto.CollectNotifyMessage;
import com.chua.starter.collect.notify.CollectNotifyChannel;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * 邮件通知渠道。
 *
 * <p>直接用 Spring Boot 邮件自动配置的 {@link JavaMailSender}（{@code spring.mail.*}），
 * 不依赖 common-starter 里的 {@code MailService}——采集宿主的扫描范围不含
 * {@code com.chua.starter.common.support}，用 MailService 会永远报"未配置邮件账号"。
 * 没配邮件账号时该渠道仍然在列表里，但发送会直接回一句可读的失败原因，
 * 让配置缺失在"最近结果"里可见而不是静默跳过。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "plugin.collect.notify", name = "enable", havingValue = "true", matchIfMissing = true)
public class CollectEmailNotifyChannel implements CollectNotifyChannel {

    /**
     * 发件人，可用系统属性 spring.mail.from 覆盖。
     */
    private static final String DEFAULT_FROM = "no-reply@example.com";

    /**
     * 邮件发送器，未配置 spring.mail.host 时容器内没有该 Bean。
     */
    private final ObjectProvider<JavaMailSender> mailSenderProvider;

    @Override
    public String channel() {
        return "email";
    }

    @Override
    public String label() {
        return "邮件";
    }

    @Override
    public String targetHint() {
        return "收件邮箱，多个用逗号分隔";
    }

    @Override
    public MessageResponse send(CollectNotifyMessage message) {
        JavaMailSender mailSender = mailSenderProvider.getIfAvailable();
        if (mailSender == null) {
            return MessageResponse.failure("未配置邮件账号（spring.mail.host），邮件渠道不可用");
        }
        String[] to = recipients(message.target());
        if (to.length == 0) {
            return MessageResponse.failure("收件邮箱为空");
        }
        long start = System.currentTimeMillis();
        try {
            MimeMessage mimeMessage = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, false, StandardCharsets.UTF_8.name());
            helper.setFrom(System.getProperty("spring.mail.from", DEFAULT_FROM));
            helper.setTo(to);
            helper.setSubject(message.subject());
            helper.setText(message.content(), true);
            mailSender.send(mimeMessage);
            long cost = System.currentTimeMillis() - start;
            return MessageResponse.builder()
                    .success(true)
                    .messageId(message.target())
                    .durationMillis(cost)
                    .build();
        } catch (Exception ex) {
            log.warn("[collect] 邮件通知发送失败 target={} cause={}", message.target(), ex.getMessage());
            return MessageResponse.builder()
                    .success(false)
                    .errorMessage("邮件发送失败: " + ex.getMessage())
                    .durationMillis(System.currentTimeMillis() - start)
                    .build();
        }
    }

    /**
     * 拆收件人：逗号（中英文）分隔，去空白。
     *
     * @param target 告警目标
     * @return 收件人数组
     */
    private String[] recipients(String target) {
        if (target == null || target.isBlank()) {
            return new String[0];
        }
        List<String> list = new ArrayList<>();
        for (String item : target.split("[,，;；]")) {
            String trimmed = item.trim();
            if (!trimmed.isEmpty()) {
                list.add(trimmed);
            }
        }
        return list.toArray(String[]::new);
    }
}
