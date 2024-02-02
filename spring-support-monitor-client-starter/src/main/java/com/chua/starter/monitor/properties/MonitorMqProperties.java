package com.chua.starter.monitor.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import static com.chua.starter.monitor.properties.MonitorMqProperties.PRE;

/**
 * 监视器属性
 *
 * @author CH
 * @version 1.0.0
 * @since 2024/01/31
 */
@Data
@ConfigurationProperties(prefix = PRE, ignoreInvalidFields = true)
public class MonitorMqProperties {

    public static final String PRE = "plugin.monitor.mq";
    /**
     * mq端口
     */
    private String host = "127.0.0.1";
    /**
     * mq端口
     */
    private int port = 31112;

    /**
     * mq订阅者
     */
    private String subscriber = "monitor";

}
