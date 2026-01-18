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
     * 配置前缀
     */
    public static final String PRE = "plugin.elasticsearch";

    /**
     * 是否启用
     */
    private boolean enable = false;

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

    // Lombok @Data 生成的 getter/setter 方法（如果 Lombok 未生效，这些方法会被使用）
    public boolean isEnable() {
        return enable;
    }

    public void setEnable(boolean enable) {
        this.enable = enable;
    }

    public String getSchema() {
        return schema;
    }

    public void setSchema(String schema) {
        this.schema = schema;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public int getConnectTimeoutMs() {
        return connectTimeoutMs;
    }

    public void setConnectTimeoutMs(int connectTimeoutMs) {
        this.connectTimeoutMs = connectTimeoutMs;
    }

    public int getSocketTimeoutMs() {
        return socketTimeoutMs;
    }

    public void setSocketTimeoutMs(int socketTimeoutMs) {
        this.socketTimeoutMs = socketTimeoutMs;
    }

    public int getConnectionRequestTimeoutMs() {
        return connectionRequestTimeoutMs;
    }

    public void setConnectionRequestTimeoutMs(int connectionRequestTimeoutMs) {
        this.connectionRequestTimeoutMs = connectionRequestTimeoutMs;
    }

    public int getMaxConnectNum() {
        return maxConnectNum;
    }

    public void setMaxConnectNum(int maxConnectNum) {
        this.maxConnectNum = maxConnectNum;
    }

    public int getMaxConnectPerRoute() {
        return maxConnectPerRoute;
    }

    public void setMaxConnectPerRoute(int maxConnectPerRoute) {
        this.maxConnectPerRoute = maxConnectPerRoute;
    }
}
