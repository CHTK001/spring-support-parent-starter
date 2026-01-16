package com.chua.starter.common.support.actuator;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Actuator配置属性
 *
 * @author CH
 * @version 1.0.0
 * @since 2024/6/21
 */
@ConfigurationProperties(prefix = "plugin.actuator", ignoreInvalidFields = true)
public class ActuatorProperties {
    /**
     * 是否启用
     */
    private boolean enable = false;


    /**
     * 是否开启认证
     */
    private boolean enableAuth = false;

    /**
     * 用户名
     */
    private String username = "admin";

    /**
     * 密码
     */
    private String password = "admin";

    /**
     * IP白名单
     */
    private Set<String> ipWhitelist = new LinkedHashSet<>();

    /**
     * 排除的路径
     */
    private Set<String> excludePaths = new LinkedHashSet<>();
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
     * 获取 enableAuth
     *
     * @return enableAuth
     */
    public boolean getEnableAuth() {
        return enableAuth;
    }

    /**
     * 设置 enableAuth
     *
     * @param enableAuth enableAuth
     */
    public void setEnableAuth(boolean enableAuth) {
        this.enableAuth = enableAuth;
    }

    /**
     * 获取 username
     *
     * @return username
     */
    public String getUsername() {
        return username;
    }

    /**
     * 设置 username
     *
     * @param username username
     */
    public void setUsername(String username) {
        this.username = username;
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
     * 获取 ipWhitelist
     *
     * @return ipWhitelist
     */
    public Set<String> getIpWhitelist() {
        return ipWhitelist;
    }

    /**
     * 设置 ipWhitelist
     *
     * @param ipWhitelist ipWhitelist
     */
    public void setIpWhitelist(Set<String> ipWhitelist) {
        this.ipWhitelist = ipWhitelist;
    }

    /**
     * 获取 excludePaths
     *
     * @return excludePaths
     */
    public Set<String> getExcludePaths() {
        return excludePaths;
    }

    /**
     * 设置 excludePaths
     *
     * @param excludePaths excludePaths
     */
    public void setExcludePaths(Set<String> excludePaths) {
        this.excludePaths = excludePaths;
    }


