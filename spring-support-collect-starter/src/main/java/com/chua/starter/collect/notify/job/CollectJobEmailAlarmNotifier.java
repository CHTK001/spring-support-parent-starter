package com.chua.starter.collect.notify.job;

import com.chua.starter.job.support.notify.JobAlarmMessage;
import com.chua.starter.job.support.notify.JobAlarmNotifier;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * 调度任务的邮件告警通道（类型 EMAIL）。
 *
 * <p>直接用 Spring Boot 邮件自动配置的 {@link JavaMailSender}（{@code spring.mail.*}），
 * 不依赖 common-starter 里那个只在特定扫描包下才存在的 {@code MailService}——
 * 采集宿主的扫描范围不含 {@code com.chua.starter.common.support}，用 MailService 会永远
 * 报"未配置邮件账号"。目标为收件邮箱（多个逗号分隔）。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "plugin.collect", name = "enable", havingValue = "true", matchIfMissing = true)
@ConditionalOnClass(JobAlarmNotifier.class)
public class CollectJobEmailAlarmNotifier implements JobAlarmNotifier {

    /**
     * 时间展示格式，与任务日志一致。
     */
    private static final DateTimeFormatter TIME_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());

    /**
     * 发件人，可用系统属性 spring.mail.from 覆盖。
     */
    private static final String DEFAULT_FROM = "no-reply@example.com";

    /**
     * 邮件发送器，未配置 spring.mail.host 时容器内没有该 Bean。
     */
    private final ObjectProvider<JavaMailSender> mailSenderProvider;

    @Override
    public String type() {
        return JobAlarmMessage.TYPE_EMAIL;
    }

    @Override
    public void notify(JobAlarmMessage message) throws Exception {
        JavaMailSender sender = mailSenderProvider.getIfAvailable();
        if (sender == null) {
            throw new IllegalStateException("未配置邮件账号（spring.mail.host），邮件告警不可用");
        }
        String[] targets = recipients(message.target());
        if (targets.length == 0) {
            throw new IllegalStateException("告警目标里没有有效的收件邮箱");
        }
        MimeMessage mimeMessage = sender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, false, StandardCharsets.UTF_8.name());
        helper.setFrom(System.getProperty("spring.mail.from", DEFAULT_FROM));
        helper.setTo(targets);
        helper.setSubject(subject(message));
        helper.setText(content(message), false);
        sender.send(mimeMessage);
        log.info("[collect] 调度任务邮件告警已发送 job={} target={}", message.jobName(), message.target());
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

    /**
     * 告警邮件标题。
     *
     * @param message 告警消息
     * @return 标题
     */
    private String subject(JobAlarmMessage message) {
        return "【调度任务失败】" + safe(message.jobName());
    }

    /**
     * 告警邮件正文：任务、节点、耗时、时刻与失败原因。
     *
     * @param message 告警消息
     * @return 正文
     */
    private String content(JobAlarmMessage message) {
        return "任务: " + safe(message.jobName()) + "\n"
                + "节点: " + safe(message.nodeId()) + "\n"
                + "耗时: " + message.costMs() + "ms\n"
                + "时刻: " + TIME_FORMAT.format(Instant.ofEpochMilli(
                        message.occurredAtMs() > 0 ? message.occurredAtMs() : System.currentTimeMillis())) + "\n"
                + "原因:\n" + safe(message.exception()) + "\n";
    }

    /**
     * 空值兜底。
     *
     * @param text 文本
     * @return 非空文本
     */
    private String safe(String text) {
        return text == null || text.isBlank() ? "—" : text;
    }
}
