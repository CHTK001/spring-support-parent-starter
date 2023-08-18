package com.chua.starter.rpc.support.properties;

import lombok.Data;
import org.apache.dubbo.config.*;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

import java.util.List;
import java.util.Set;

/**
 * 配置
 *
 * @author CH
 */
@Data
@ConfigurationProperties(prefix = RpcProperties.PRE, ignoreInvalidFields = true, ignoreUnknownFields = true)
public class RpcProperties {

    public static final String PRE = "plugin.rpc";
    public static final String DEFAULT_SERIALIZATION = "hessian";
    /**
     * 是否开启
     */
    private boolean open;
    /**
     * 扫描
     */
    private Set<String> scan;
    /**
     * 是否开启权限校验
     */
    private boolean enable;
    /**
     * ak
     */
    private String accessKey = "331000";
    /**
     * sk
     */
    private String secretKey = "St0ojc";
    /**
     * 服务序列
     */
    private String serviceKey = "98bb5008c946bdce065af13070241e10";
    /**
     * crypto
     */
    private String crypto = "aes";
    /**
     * 协议
     */
    @NestedConfigurationProperty
    private List<ProtocolConfig> protocols;
    /**
     * 应用
     */
    @NestedConfigurationProperty
    private ApplicationConfig application;
    /**
     * 消费者
     */
    @NestedConfigurationProperty
    private ConsumerConfig consumer;
    /**
     * 监控器
     */
    @NestedConfigurationProperty
    private MonitorConfig monitor;
    /**
     * 注册器
     */
    @NestedConfigurationProperty
    private RegistryConfig registry;
    /**
     * 注册器
     */
    @NestedConfigurationProperty
    private ReferenceConfig reference;

}
