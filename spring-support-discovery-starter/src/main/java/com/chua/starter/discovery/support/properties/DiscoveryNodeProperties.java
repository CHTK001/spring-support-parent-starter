package com.chua.starter.discovery.support.properties;

import lombok.Data;

/**
 * @author CH
 * @since 2024/12/26
 */
@Data
public class DiscoveryNodeProperties {
    /**
     * 服务id
     */
    private String serverId;

    /**
     * uri
     */
    private String namespace;
    /**
     * 协议
     */
    private String protocol = "http";
}
