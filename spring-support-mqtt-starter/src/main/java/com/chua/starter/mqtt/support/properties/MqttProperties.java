package com.chua.starter.mqtt.support.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import static com.chua.starter.mqtt.support.properties.MqttProperties.PRE;

/**
 * mqtt
 * @author CH
 */
@Data
@ConfigurationProperties(prefix = PRE, ignoreInvalidFields = true)
public class MqttProperties {

    public static final String PRE = "plugin.spring.mqtt";

    /**
     * 是否启用
     */
    private boolean enable = false;
    /**
     * 主机
     */
    private String address;
    /**
     * 账号
     */
    private String name;
    /**
     * 密码
     */
    private String password;
    /**
     * 唯一ID
     */
    private String clientId;
    /**
     * 超时时间
     */
    private int timeout = 20;

    /**
     * 保活时间
     */
    private int keepalive = 20;
}
