package com.chua.starter.elasticsearch.support.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 配置文件
 *
 * @author CH
 */
@Data
@ConfigurationProperties(prefix = ElasticSearchProperties.PRE)
public class ElasticSearchProperties {
    /**
     * 是否启用
     */
    private boolean enable = false;



    public static final String PRE = "plugin.elasticsearch";
    /**
     * 协议
     */
    private String schema = "http";
    /**
     * 地址
     */
    private String address;
    /**
     * 连接超时时间
     */
    private int connectTimeoutMs = 5000;
    /**
     * 连接超时时间
     */
    private int socketTimeoutMs = 5000;
    /**
     * 连接超时时间
     */
    private int connectionRequestTimeoutMs = 5000;
    /**
     * 最大连接数量
     */
    private int maxConnectNum = 100;
    /**
     * 最大连接数量
     */
    private int maxConnectPerRoute = 100;
}
