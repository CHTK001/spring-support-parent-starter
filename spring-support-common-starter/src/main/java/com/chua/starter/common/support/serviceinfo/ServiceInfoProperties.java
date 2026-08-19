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
     * Basic Auth 用户名。
     */
    private String authUsername = "admin";

    /**
     * Basic Auth 密码。
     */
    private String authPassword = "admin";

    /**
     * IP 白名单, 命中后跳过认证。支持精确匹配、通配符、CIDR。
     */
    private Set<String> authIpWhitelist = new LinkedHashSet<>();

    public boolean isEnable() {
        return enable;
    }

    public void setEnable(boolean enable) {
        this.enable = enable;
    }

    public boolean isBanner() {
        return banner;
    }

    public void setBanner(boolean banner) {
        this.banner = banner;
    }

    public boolean isAuthEnable() {
        return authEnable;
    }

    public void setAuthEnable(boolean authEnable) {
        this.authEnable = authEnable;
    }

    public String getAuthUsername() {
        return authUsername;
    }

    public void setAuthUsername(String authUsername) {
        this.authUsername = authUsername;
    }

    public String getAuthPassword() {
        return authPassword;
    }

    public void setAuthPassword(String authPassword) {
        this.authPassword = authPassword;
    }

    public Set<String> getAuthIpWhitelist() {
        return authIpWhitelist;
    }

    public void setAuthIpWhitelist(Set<String> authIpWhitelist) {
        this.authIpWhitelist = authIpWhitelist;
    }
}
