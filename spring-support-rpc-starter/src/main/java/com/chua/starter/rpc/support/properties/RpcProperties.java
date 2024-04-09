package com.chua.starter.rpc.support.properties;

import com.chua.common.support.rpc.RpcConsumerConfig;
import com.chua.common.support.rpc.RpcProtocolConfig;
import com.chua.common.support.rpc.RpcRegistryConfig;
import com.chua.common.support.rpc.enums.RpcType;
import com.chua.common.support.utils.NumberUtils;
import com.chua.starter.common.support.configuration.SpringBeanUtils;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

import java.util.Collections;
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
     * 实现
     */
    private RpcType type = RpcType.DUBBO;
    /**
     * 协议
     */
    @NestedConfigurationProperty
    private List<RpcProtocolConfig> protocols;
    /**
     * 应用
     */
    private String applicationName = "${spring.application.name:app}";
    /**
     * 消费者
     */
    @NestedConfigurationProperty
    private RpcConsumerConfig consumer;
    /**
     * 注册器
     */
    @NestedConfigurationProperty
    private List<RpcRegistryConfig> registry;

    public List<RpcRegistryConfig> getRegistry() {
        if(null == registry) {
            return Collections.emptyList();
        }

        for (RpcRegistryConfig config : registry) {
            config.setAddress(SpringBeanUtils.resolvePlaceholders(config.getAddress()));
            config.setUsername(SpringBeanUtils.resolvePlaceholders(config.getUsername()));
            config.setPassword(SpringBeanUtils.resolvePlaceholders(config.getPassword()));
        }
        return registry;
    }

    public List<RpcProtocolConfig> getProtocols() {
        if(null == protocols) {
            return Collections.emptyList();
        }

        for (RpcProtocolConfig config : protocols) {
            config.setHost(SpringBeanUtils.resolvePlaceholders(config.getHost()));
            config.setPort(NumberUtils.toInt(SpringBeanUtils.resolvePlaceholders(config.getPort() + "")));
            config.setName(SpringBeanUtils.resolvePlaceholders(config.getName()));
        }
        return protocols;
    }

    public String getApplicationName() {
        return SpringBeanUtils.resolvePlaceholders(applicationName);
    }
}
