package com.chua.starter.monitor.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import static com.chua.starter.monitor.properties.MonitorProperties.PRE;

/**
 * 监视器属性
 *
 * @author CH
 * @version 1.0.0
 * @since 2024/01/31
 */
@Data
@ConfigurationProperties(prefix = PRE, ignoreInvalidFields = true)
public class MonitorProperties {

    public static final String PRE = "plugin.monitor";

    /**
     * 加密模式
     */
    private String encryptionSchema = "aes";

    /**
     * 加密密钥
     */
    private String encryptionKey = "123456";
    /**
     * 打开
     */
    private boolean enable = false;

    /**
     * 统一服务端地址
     */
    private String host = "127.0.0.1";
    /**
     * 客户端绑定的端口
     */
    private int port = 31111;
    /**
     * mq端口
     */
    private String mqHost = "127.0.0.1";
    /**
     * mq端口
     */
    private int mqPort = 31112;

    /**
     * 协议
     */
    private String protocol = "http";
}
