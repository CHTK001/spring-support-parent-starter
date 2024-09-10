package com.chua.starter.discovery.support.properties;

import lombok.Data;

/**
 * 发现配置
 * @author CH
 * @since 2024/9/9
 */
@Data
public class DiscoveryProperties {

    /**
     * 订阅服务
     */
    private String subscribe;

    /**
     * 服务id
     */
    private String serverId;

    /**
     * 是否启用
     */
    private boolean enabled = true;
    /**
     * 地址
     */
    private String address;
    /**
     * 账号
     */
    private String username;

    /**
     * 密码
     */
    private String password;
    /**
     * 协议
     */
    private String protocol;


    /**
     * uri
     */
    private String namespace = "discovery";
    /**
     * 连接超时时间
     */
    private int connectionTimeoutMillis = 10_000;
    /**
     * 会话超时时间
     */
    private int sessionTimeoutMillis = 10_000;

}
