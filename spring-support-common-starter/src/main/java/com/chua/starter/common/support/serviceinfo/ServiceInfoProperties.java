package com.chua.starter.common.support.serviceinfo;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * ServiceInfo 配置属性。
 *
 * <p>前缀 {@code plugin.service-info}。用于配置 {@code /service-info} 端点的认证、
 * 横幅渲染等行为。</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
@ConfigurationProperties(prefix = "plugin.service-info", ignoreInvalidFields = true)
public class ServiceInfoProperties {

    /**
     * 是否启用 ServiceInfo 端点 ({@code /service-info}) 和横幅渲染。
     * 默认 true。
     */
    private boolean enable = true;

    /**
     * 是否在启动时输出横幅到 stdout。
     * 默认 true。
     */
    private boolean banner = true;

    /**
     * 是否对 {@code /service-info} 端点启用认证。
     * 默认 false (内网场景)。
     */
    private boolean authEnable = false;

    /**
     * 是否在横幅中展示基础设施连接信息 (Redis/JDBC/Kafka/RabbitMQ/SpringCloud/Dubbo)。
     *
     * <p>默认 true, 但仅展示 {@link Environment} 中真实配置了的条目;
     * 未配置的中间件不会出现在横幅中。</p>
     */
    private boolean infrastructure = true;

    /**
     * Basic Auth 用户名。
     */
    private String authUsername = "admin";

    /**
     * Basic Auth 密码（通过配置文件覆盖）。
     */
    private String authPassword = "";

    /**
     * IP 白名单, 命中后跳过认证。支持精确匹配、通配符、CIDR。
     */
    private Set<String> authIpWhitelist = new LinkedHashSet<>();

    /** 是否启用 */
    public boolean isEnable() {
        return enable;
    }

    /** 设置启用 */
    public void setEnable(boolean enable) {
        this.enable = enable;
    }

    /** 是否Banner */
    public boolean isBanner() {
        return banner;
    }

    /** 设置Banner */
    public void setBanner(boolean banner) {
        this.banner = banner;
    }

    /** 是否Auth启用 */
    public boolean isAuthEnable() {
        return authEnable;
    }

    /** 设置Auth启用 */
    public void setAuthEnable(boolean authEnable) {
        this.authEnable = authEnable;
    }

    /** 是否展示基础设施连接信息 */
    public boolean isInfrastructure() {
        return infrastructure;
    }

    /** 设置是否展示基础设施连接信息 */
    public void setInfrastructure(boolean infrastructure) {
        this.infrastructure = infrastructure;
    }

    /** 获取AuthUsername */
    public String getAuthUsername() {
        return authUsername;
    }

    /** 设置AuthUsername */
    public void setAuthUsername(String authUsername) {
        this.authUsername = authUsername;
    }

    /** 获取AuthPassword */
    public String getAuthPassword() {
        return authPassword;
    }

    /** 设置AuthPassword */
    public void setAuthPassword(String authPassword) {
        this.authPassword = authPassword;
    }

    /** 获取AuthIpWhitelist */
    public Set<String> getAuthIpWhitelist() {
        return authIpWhitelist;
    }

    /** 设置AuthIpWhitelist */
    public void setAuthIpWhitelist(Set<String> authIpWhitelist) {
        this.authIpWhitelist = authIpWhitelist;
    }
}
