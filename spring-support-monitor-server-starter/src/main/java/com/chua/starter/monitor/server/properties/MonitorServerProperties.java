package com.chua.starter.monitor.server.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import static com.chua.starter.monitor.server.properties.MonitorServerProperties.PRE;

/**
 * 监视服务器属性
 *
 * @author CH
 * @version 1.0.0
 * @since 2024/01/31
 */
@Data
@ConfigurationProperties(prefix = PRE, ignoreInvalidFields = true)
public class MonitorServerProperties {

    public static final String PRE = "plugin.monitor.server";

    /**
     * mq订阅者
     */
    private String mqSubscriber = "monitor";
    /**
     * mq端口
     */
    private int mqPort = 31112;

    /**
     * mq主机
     */
    private String mqHost = "0.0.0.0";

    /**
     * 加密模式
     */
    private String encryptionSchema = "aes";

    /**
     * 加密密钥
     */
    private String encryptionKey = "123456";

    /**
     * 报表数据保持活动(s)
     */
    private long reportDataKeepAlive = 60 * 60L;
}
