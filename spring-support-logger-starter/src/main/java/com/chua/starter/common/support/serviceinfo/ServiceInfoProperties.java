package com.chua.starter.common.support.serviceinfo;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 服务信息 配置属性。
 *
 * <p>前缀 {@code plugin.service-info}。用于配置 {@code /service-info} 端点的认证、
 * 横幅渲染等行为。</p>
 *
 * <p><b>完整 YAML 配置示例：</b></p>
 * <pre>{@code
 * plugin:
 *   service-info:
 *     enable: true              # 是否启用 /service-info 端点与横幅（默认 true）
 *     banner: true              # 启动时是否输出横幅（默认 true）
 *     auth-enable: false        # 是否启用端点认证（默认 false，内网场景）
 *     auth-username: admin      # 认证用户名
 *     auth-password: change-me  # 认证密码（占位符，请替换）
 *     auth-ip-whitelist:        # IP 白名单（精确/通配符/CIDR），命中跳过认证
 *       - 127.0.0.1
 *       - 192.168.0.0/16
 *     infrastructure: true      # 横幅是否展示基础设施连接信息（默认 true）
 * }</pre>
 *
 * @since 4.0.0.42
 * @author CH
 */
@ConfigurationProperties(prefix = "plugin.service-info", ignoreInvalidFields = true)
public class ServiceInfoProperties {

    /**
     * 是否启用 服务信息 端点 ({@code /service-info}) 和横幅渲染。
     * 默认 true。
     *
     * <p>YAML 示例：</p>
     * <pre>{@code
     * plugin:
     *   service-info:
     *     enable: true
     * }</pre>
     */
    private boolean enable = true;

    /**
     * 是否在启动时输出横幅到 stdout。
     * 默认 true。
     *
     * <p>YAML 示例：</p>
     * <pre>{@code
     * plugin:
     *   service-info:
     *     banner: true
     * }</pre>
     */
    private boolean banner = true;

    /**
     * 是否对 {@code /service-info} 端点启用认证。
     * 默认 false (内网场景)。
     *
     * <p>认证过滤器的注册条件为 {@code plugin.service-info.auth.enable=true}；
     * 认证方式为基础用户名/密码，命中 IP 白名单时跳过。</p>
     *
     * <p>YAML 示例：</p>
     * <pre>{@code
     * plugin:
     *   service-info:
     *     auth-enable: true
     * }</pre>
     */
    private boolean authEnable = false;

    /**
     * 是否在横幅中展示基础设施连接信息 (Redis/JDBC/Kafka/RabbitMQ/springcloud/Dubbo)。
     *
     * <p>默认 true, 但仅展示 {@link Environment} 中真实配置了的条目;
     * 未配置的中间件不会出现在横幅中。</p>
     *
     * <p>YAML 示例：</p>
     * <pre>{@code
     * plugin:
     *   service-info:
     *     infrastructure: true
     * }</pre>
     */
    private boolean infrastructure = true;

    /**
     * 基础 认证 用户名。
     *
     * <p>默认 {@code admin}。</p>
     *
     * <p>YAML 示例：</p>
     * <pre>{@code
     * plugin:
     *   service-info:
     *     auth-username: admin
     * }</pre>
     */
    private String authUsername = "admin";

    /**
     * 基础 认证 密码（通过配置文件覆盖）。
     *
     * <p>默认 {@code ""}；启用认证时必须显式配置（示例值均为占位符）。</p>
     *
     * <p>YAML 示例：</p>
     * <pre>{@code
     * plugin:
     *   service-info:
     *     auth-password: change-me
     * }</pre>
     */
    private String authPassword = "";

    /**
     * IP 白名单, 命中后跳过认证。支持精确匹配、通配符、CIDR。
     *
     * <p>默认空集合（不放行任何 IP，全部走认证）。</p>
     *
     * <p>YAML 示例：</p>
     * <pre>{@code
     * plugin:
     *   service-info:
     *     auth-ip-whitelist:
     *       - 127.0.0.1
     *       - 192.168.0.0/16
     * }</pre>
     */
    private Set<String> authIpWhitelist = new LinkedHashSet<>();

    /**
     * 是否启用
     *
     * @return 是否enable的结果
     */
    public boolean isEnable() {
        return enable;
    }

    /**
     * 设置启用
     *
     * @param enable enable
     */
    public void setEnable(boolean enable) {
        this.enable = enable;
    }

    /**
     * 是否Banner
     *
     * @return 是否banner的结果
     */
    public boolean isBanner() {
        return banner;
    }

    /**
     * 设置Banner
     *
     * @param banner banner
     */
    public void setBanner(boolean banner) {
        this.banner = banner;
    }

    /**
     * 是否认证启用
     *
     * @return 是否认证enable的结果
     */
    public boolean isAuthEnable() {
        return authEnable;
    }

    /**
     * 设置认证启用
     *
     * @param authEnable 认证enable
     */
    public void setAuthEnable(boolean authEnable) {
        this.authEnable = authEnable;
    }

    /**
     * 是否展示基础设施连接信息
     *
     * @return 是否infrastructure的结果
     */
    public boolean isInfrastructure() {
        return infrastructure;
    }

    /**
     * 设置是否展示基础设施连接信息
     *
     * @param infrastructure infrastructure
     */
    public void setInfrastructure(boolean infrastructure) {
        this.infrastructure = infrastructure;
    }

    /**
     * 获取认证用户名
     *
     * @return 获取认证用户名的结果
     */
    public String getAuthUsername() {
        return authUsername;
    }

    /**
     * 设置认证用户名
     *
     * @param authUsername 认证用户名
     */
    public void setAuthUsername(String authUsername) {
        this.authUsername = authUsername;
    }

    /**
     * 获取认证密码
     *
     * @return 获取认证密码的结果
     */
    public String getAuthPassword() {
        return authPassword;
    }

    /**
     * 设置认证密码
     *
     * @param authPassword 认证密码
     */
    public void setAuthPassword(String authPassword) {
        this.authPassword = authPassword;
    }

    /**
     * 获取认证ipwhitelist
     *
     * @return 获取认证ipwhitelist的结果
     */
    public Set<String> getAuthIpWhitelist() {
        return authIpWhitelist;
    }

    /**
     * 设置认证ipwhitelist
     *
     * @param authIpWhitelist 认证ipwhitelist
     */
    public void setAuthIpWhitelist(Set<String> authIpWhitelist) {
        this.authIpWhitelist = authIpWhitelist;
    }
}
