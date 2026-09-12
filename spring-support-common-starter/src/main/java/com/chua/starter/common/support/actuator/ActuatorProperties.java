package com.chua.starter.common.support.actuator;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Actuator配置属性
 *
   * @版本 1.0.0
 * @since 2024/6/21
 * @author CH
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
    private String username = "";

    /**
     * 密码
     */
    private String password = "";

    /**
     * IP白名单
     */
    private Set<String> ipWhitelist = new LinkedHashSet<>();

    /**
     * 排除的路径
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
