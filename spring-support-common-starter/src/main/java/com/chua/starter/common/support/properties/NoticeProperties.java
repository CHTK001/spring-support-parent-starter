package com.chua.starter.common.support.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;


/**
 * 通知配置
 * @author CH
 * @since 2024/8/7
 */
@ConfigurationProperties(prefix = NoticeProperties.PRE, ignoreInvalidFields = true)
public class NoticeProperties {
    /**
     * 是否启用
     */
    private boolean enable = false;


    public static final String PRE = "plugin.notice";

    private Email email;


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
    /**
     * 获取 enable
     *
     * @return enable
     */
    public boolean getEnable() {
        return enable;
    }

    /**
     * 设置 enable
     *
     * @param enable enable
     */
    public void setEnable(boolean enable) {
        this.enable = enable;
    }

    /**
     * 获取 email
     *
     * @return email
     */
    public Email getEmail() {
        return email;
    }

    /**
     * 设置 email
     *
     * @param email email
     */
    public void setEmail(Email email) {
        this.email = email;
    }

    /**
     * 获取 from
     *
     * @return from
     */
    public String getFrom() {
        return from;
    }

    /**
     * 设置 from
     *
     * @param from from
     */
    public void setFrom(String from) {
        this.from = from;
    }

    /**
     * 获取 smtpHost
     *
     * @return smtpHost
     */
    public String getSmtpHost() {
        return smtpHost;
    }

    /**
     * 设置 smtpHost
     *
     * @param smtpHost smtpHost
     */
    public void setSmtpHost(String smtpHost) {
        this.smtpHost = smtpHost;
    }

    /**
     * 获取 sslSmtpPort
     *
     * @return sslSmtpPort
     */
    public String getSslSmtpPort() {
        return sslSmtpPort;
    }

    /**
     * 设置 sslSmtpPort
     *
     * @param sslSmtpPort sslSmtpPort
     */
    public void setSslSmtpPort(String sslSmtpPort) {
        this.sslSmtpPort = sslSmtpPort;
    }

    /**
     * 获取 password
     *
     * @return password
     */
    public String getPassword() {
        return password;
    }

    /**
     * 设置 password
     *
     * @param password password
     */
    public void setPassword(String password) {
        this.password = password;
    }

    /**
     * 获取 smtpPort
     *
     * @return smtpPort
     */
    public int getSmtpPort() {
        return smtpPort;
    }

    /**
     * 设置 smtpPort
     *
     * @param smtpPort smtpPort
     */
    public void setSmtpPort(int smtpPort) {
        this.smtpPort = smtpPort;
    }


}

