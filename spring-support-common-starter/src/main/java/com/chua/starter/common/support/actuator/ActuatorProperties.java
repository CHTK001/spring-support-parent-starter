package com.chua.starter.common.support.actuator;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Actuator 端点加固配置属性。
 *
 * <p>前缀 {@code plugin.actuator}。为 Spring Boot Actuator 端点提供基础认证、
 * IP 白名单与路径排除等防护能力。</p>
 *
 * <h3>配置示例</h3>
 * <pre>
 * plugin:
 *   actuator:
 *     enable: false            # 是否启用加固（字段默认 false；端点装配 matchIfMissing=true）
 *     enable-auth: false       # 是否开启基础认证（默认 false；需显式开启）
 *     username: admin          # 认证用户名（默认空）
 *     password: your-password  # 认证密码（生产从环境变量注入）
 *     ip-whitelist:            # IP 白名单（支持精确/通配符/CIDR）
 *       - 127.0.0.1
 *       - 192.168.0.0/16
 *     exclude-paths:           # 不参与加固的路径
 *       - /actuator/health
 * </pre>
 *
 * @since 2024/6/21
 * @author CH
 */
@ConfigurationProperties(prefix = "plugin.actuator", ignoreInvalidFields = true)
public class ActuatorProperties {
    /**
     * 是否启用 Actuator 加固。
     * <p>YAML：{@code plugin.actuator.enable}，字段默认 {@code false}；端点装配条件为
     * {@code matchIfMissing = true}（未配置时默认装配）。</p>
     */
    private boolean enable = false;


    /**
     * 是否开启基础认证。
     * <p>YAML：{@code plugin.actuator.enable-auth}，默认 {@code false}；开启后需同时配置
     * {@code username} 与 {@code password}。</p>
     */
    private boolean enableAuth = false;

    /**
     * 基础认证用户名。
     * <p>YAML：{@code plugin.actuator.username}，默认空字符串。</p>
     */
    private String username = "";

    /**
     * 基础认证密码。
     * <p>YAML：{@code plugin.actuator.password}，默认空字符串；生产环境建议从环境变量注入。</p>
     */
    private String password = "";

    /**
     * IP 白名单，命中后放行（支持精确匹配、通配符、CIDR）。
     * <p>YAML：{@code plugin.actuator.ip-whitelist}，默认空集合。</p>
     */
    private Set<String> ipWhitelist = new LinkedHashSet<>();

    /**
     * 排除的路径，这些路径不参与加固。
     * <p>YAML：{@code plugin.actuator.exclude-paths}，默认空集合。</p>
     */
    private Set<String> excludePaths = new LinkedHashSet<>();

    /**
     * 获取 enable
     *
     * @return enable
     */
    public boolean getEnable() {
        return enable;
    }

    /**
     * 判断是否启用
     *
     * @return enable
     */
    public boolean isEnable() {
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
     * 获取 enable认证
     *
     * @return enableAuth
     */
    public boolean getEnableAuth() {
        return enableAuth;
    }

    /**
     * 判断是否开启认证
     *
     * @return enableAuth
     */
    public boolean isEnableAuth() {
        return enableAuth;
    }

    /**
     * 设置 enable认证
     *
     * @param enableAuth enable认证
     */
    public void setEnableAuth(boolean enableAuth) {
        this.enableAuth = enableAuth;
    }

    /**
     * 获取 用户名
     *
     * @return username
     */
    public String getUsername() {
        return username;
    }

    /**
     * 设置 用户名
     *
     * @param username 用户名
     */
    public void setUsername(String username) {
        this.username = username;
    }

    /**
     * 获取 密码
     *
     * @return password
     */
    public String getPassword() {
        return password;
    }

    /**
     * 设置 密码
     *
     * @param password 密码
     */
    public void setPassword(String password) {
        this.password = password;
    }

    /**
     * 获取 ipwhitelist
     *
     * @return ipWhitelist
     */
    public Set<String> getIpWhitelist() {
        return ipWhitelist;
    }

    /**
     * 设置 ipwhitelist
     *
     * @param ipWhitelist ipwhitelist
     */
    public void setIpWhitelist(Set<String> ipWhitelist) {
        this.ipWhitelist = ipWhitelist;
    }

    /**
     * 获取 exclude路径
     *
     * @return excludePaths
     */
    public Set<String> getExcludePaths() {
        return excludePaths;
    }

    /**
     * 设置 exclude路径
     *
     * @param excludePaths exclude路径
     */
    public void setExcludePaths(Set<String> excludePaths) {
        this.excludePaths = excludePaths;
    }
}
