package com.chua.starter.ai.support.manage.config;

import jakarta.mail.internet.MimeMessage;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessagePreparator;

import java.io.InputStream;

/**
 * 本地开发环境的邮件服务 Mock 配置。
 * 当没有配置真实的 JavaMailSender 时，提供空实现。
 */
@Configuration
public class LocalMailMockConfig {

    @Bean
    @ConditionalOnMissingBean
    public JavaMailSender javaMailSender() {
        return new JavaMailSender() {
            @Override
            public void send(SimpleMailMessage simpleMessage) {}

            @Override
            public void send(SimpleMailMessage... simpleMessages) {}

            @Override
            public void send(MimeMessagePreparator mimeMessagePreparator) {}

            @Override
            public void send(MimeMessage... mimeMessages) {}

            @Override
            public MimeMessage createMimeMessage() { return null; }

            @Override
            public MimeMessage createMimeMessage(InputStream contentStream) { return null; }
        };
    }
}
