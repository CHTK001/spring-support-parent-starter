package com.chua.report.client.arthas.starter.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Arthas 客户端配置
 */
@Data
@ConfigurationProperties(prefix = ArthasClientProperties.PREFIX, ignoreInvalidFields = true)
public class ArthasClientProperties {

    public static final String PREFIX = "plugin.report.client.arthas";

    /**
     * 是否开启（默认开启）
     */
    private boolean enable = false;
    /**
     * agentId
     */
    private String agentId;

    /**
     * agentName
     */
    private String agentName ;
    /**
     * tunnel 服务地址，例如: ws://arthas-tunnel-server:7777/ws
     */
    private String tunnelAddress = "ws://127.0.0.1:7777/ws";

}


