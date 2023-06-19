package com.chua.starter.mqtt.support.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

import java.util.List;

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
     * 主机
     */
    private String host;
    /**
     * 账号
     */
    private String name;
    /**
     * 密码
     */
    private String password;
    /**
     * 默认qos为0，非持久化
     */
    private int qos = 0;
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
    /**
     * 主题
     */
    private List<String> topic;
}
