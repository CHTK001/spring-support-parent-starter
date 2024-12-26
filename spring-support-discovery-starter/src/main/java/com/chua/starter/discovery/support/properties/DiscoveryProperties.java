package com.chua.starter.discovery.support.properties;

import lombok.Data;

import java.util.LinkedList;
import java.util.List;

/**
 * 发现配置
 * @author CH
 * @since 2024/9/9
 */
@Data
public class DiscoveryProperties {

    /**
     * 是否启用
     */
    private boolean enabled = true;

    /**
     * 协议
     */
    private String protocol;
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
     * 连接超时时间
     */
    private int connectionTimeoutMillis = 10_000;
    /**
     * 会话超时时间
     */
    private int sessionTimeoutMillis = 10_000;

    /**
     * 节点
     */
    private List<DiscoveryNodeProperties> node = new LinkedList<>();

}
