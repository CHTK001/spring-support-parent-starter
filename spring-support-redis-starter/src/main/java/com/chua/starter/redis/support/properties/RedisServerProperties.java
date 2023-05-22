package com.chua.starter.redis.support.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * redis server
 *
 * @author CH
 */
@Data
@ConfigurationProperties(prefix = "plugin.redis.server")
public class RedisServerProperties {
    /**
     * 嵌入式服务
     */
    private boolean openEmbedded = false;
    /**
     * 端口
     */
    private Integer port = 6379;
    /**
     * 数据库
     */
    private Integer database = 0;
    /**
     * host
     */
    private String host = "0.0.0.0";
    /**
     * 最大内存
     */
    private String maxMemory = "128M";
    private String maxHeap = "100M";
}
