package com.chua.starter.proxy.support.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import static com.chua.starter.proxy.support.properties.ProxyProperties.PRE;

/**
 * 代理
 *
 * @author CH
 * @since 2024/5/11
 */
@ConfigurationProperties(prefix = PRE, ignoreInvalidFields = true)
@Data
public class ProxyProperties {

    public static final String PRE = "plugin.proxy";


    /**
     * 代理类型，默认为NACOS
     */
    private ProxyType type = ProxyType.NACOS;

    /**
     * 代理服务的URL
     */
    private String url;

    /**
     * 代理服务的权重
     */
    private int weight = 1;

    /**
     * 连接代理服务的超时时间
     */
    private int timeoutMills = 10_000;

    /**
     * 连接代理服务的用户名
     */
    private String username;

    /**
     * 连接代理服务的密码
     */
    private String password;


    /**
     * 记录绑定的主机地址。
     * 这是一个私有成员变量，用于存储应用或服务要绑定的主机地址。
     */
    private String bindHost;

    /**
     * 定义代理服务的类型
     */
    public enum ProxyType {
        /**
         * 默认代理类型，未指定时使用
         */
        DEFAULT,
        /**
         * 使用Nacos作为代理服务
         */
        NACOS,
        /**
         * 使用ZooKeeper作为代理服务
         */
        ZOOKEEPER,
        /**
         * 使用Etcd作为代理服务
         */
        ETCD
    }
}
