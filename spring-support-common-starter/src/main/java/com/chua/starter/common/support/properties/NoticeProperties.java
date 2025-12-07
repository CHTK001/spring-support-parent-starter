package com.chua.starter.common.support.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;


/**
 * 通知配置
 * @author CH
 * @since 2024/8/7
 */
@Data
@ConfigurationProperties(prefix = NoticeProperties.PRE, ignoreInvalidFields = true)
public class NoticeProperties {

    public static final String PRE = "plugin.notice";

    private Email email;


    @Data
    public static class Email {

        /**
         * 发送者
         */
        private String from;
        /**
         * 主机
         */
        private String smtpHost = "smtp.qq.com";
        /**
         * ssl smtp端口
         */
        private String sslSmtpPort = "465";
        /**
         * 授权码
         */
        private String password;

        /**
         * smtp端口
         */
        private int smtpPort = 25;
    }
}

