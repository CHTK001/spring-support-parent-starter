package com.chua.starter.common.support.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;


/**
 * 通知配置属性（邮件通知）。
 *
 * <p>前缀 {@code plugin.notice}。当前支持 SMTP 邮件通知，用于验证码告警、
 * 异常通知等场景。</p>
 *
 * <h3>配置示例</h3>
 * <pre>
 * plugin:
 *   notice:
 *     enable: false                 # 是否启用邮件通知（默认 false）
 *     email:
 *       from: no-reply@example.com  # 发件人
 *       smtp-host: smtp.qq.com      # SMTP 主机（默认 smtp.qq.com）
 *       smtp-port: 25               # SMTP 端口（默认 25）
 *       ssl-smtp-port: "465"        # SSL 端口（默认 465）
 *       password: your-auth-code    # SMTP 授权码（生产从环境变量注入）
 * </pre>
 *
 * @since 2024/8/7
 * @author CH
 */
@ConfigurationProperties(prefix = NoticeProperties.PRE, ignoreInvalidFields = true)
public class NoticeProperties {
    /**
     * 是否启用邮件通知。
     * <p>YAML：{@code plugin.notice.enable}，默认 {@code false}。</p>
     */
    private boolean enable = false;


    /**
     * PRE
    */
    public static final String PRE = "plugin.notice";

    /**
     * 邮件通知配置。
     * <p>YAML：{@code plugin.notice.email.*}；未配置时邮件通知不生效。</p>
    */
    private Email email;

    @Data
    /**
     * 邮件通知配置。
     * <p>YAML 前缀 {@code plugin.notice.email}。</p>
     *
     * @author CH
     * @since 4.0.0
     */
    public static class Email {
        /**
         * 发件人邮箱。
         * <p>YAML：{@code plugin.notice.email.from}，默认未设置。</p>
         */
        private String from;
        /**
         * SMTP 主机。
         * <p>YAML：{@code plugin.notice.email.smtp-host}，默认 {@code smtp.qq.com}。</p>
         */
        private String smtpHost = "smtp.qq.com";
        /**
         * SSL SMTP 端口。
         * <p>YAML：{@code plugin.notice.email.ssl-smtp-port}，默认 {@code 465}。</p>
         */
        private String sslSmtpPort = "465";
        /**
         * SMTP 授权码/密码。
         * <p>YAML：{@code plugin.notice.email.password}，默认未设置；生产环境建议从环境变量注入。</p>
         */
        private String password;

        /**
         * SMTP 端口。
         * <p>YAML：{@code plugin.notice.email.smtp-port}，默认 {@code 25}。</p>
         */
        // [P3C A20 豁免]
        private int smtpPort = 25;
    }
}

