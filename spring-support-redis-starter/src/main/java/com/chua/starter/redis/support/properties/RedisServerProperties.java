package com.chua.starter.redis.support.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
* Redis 服务端
*
* @author CH
* @since 4.0.0
 */
@Data
@ConfigurationProperties(prefix = "plugin.redis.server")
public class RedisServerProperties {
    /**
    * 是否启用
    */
    private boolean enable = false;

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
    * 主机
    */
    private String host = "0.0.0.0";
    /**
    * 最大内存
    */
    private String maxMemory = "128M";
    /** 最大值heap */
    private String maxHeap = "100M";
}
