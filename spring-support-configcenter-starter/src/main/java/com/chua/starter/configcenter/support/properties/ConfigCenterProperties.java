package com.chua.starter.configcenter.support.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 配置配置
 *
 * @author CH
 * @since 2024/9/9
 */
@Data
@ConfigurationProperties(prefix = ConfigCenterProperties.PRE, ignoreInvalidFields = true)
public class ConfigCenterProperties {


    public static final String PRE = "plugin.config-center";
    /**
     * 是否启用
     */
    private boolean enable = false;
    /**
     * 连接超时
     */
    private int connectTimeout = 5000;
    /**
     * 连接超时
     */
    private int readTimeout = 5000;
    /**
     * 协议
     */
    private String protocol;
    /**
     * 地址
     */
    private String address;
    /**
     * 账号
     */
    private String username;

    /**
     * 密码
     */
    private String password;

    /**
     * 命名空间(不填写默认为 profile.active)
     */
    private String namespaceId;


}
