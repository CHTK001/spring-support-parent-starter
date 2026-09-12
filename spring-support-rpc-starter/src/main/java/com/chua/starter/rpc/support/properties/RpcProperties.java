package com.chua.starter.rpc.support.properties;

import com.chua.common.support.network.rpc.RpcConsumerConfig;
import com.chua.common.support.network.rpc.RpcProtocolConfig;
import com.chua.common.support.network.rpc.RpcRegistryConfig;
import com.chua.common.support.network.rpc.RpcType;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
* 配置
*
* @author CH
* @since 4.0.0
 */
@Data
@ConfigurationProperties(prefix = RpcProperties.PRE, ignoreInvalidFields = true, ignoreUnknownFields = true)
public class RpcProperties {

    /** PRE */
    public static final String PRE = "plugin.rpc";
    /** 默认_serialization */
    public static final String DEFAULT_SERIALIZATION = "hessian";

    /**
    * 是否启用
     */
    private boolean enable = false;
    /**
    * 扫描
     */
    private Set<String> scan;
    /**
    * ak，从配置项 rpc.access-钥匙 注入，严禁硬编码
     */
    private String accessKey = "";
    /**
    * sk，从配置项 rpc.secret-钥匙 注入，严禁硬编码
     */
    private String secretKey = "";
    /**
    * 服务序列
     */
    private String serviceKey = "98bb5008c946bdce065af13070241e10";
    /**
    * 加密货币
     */
    private String crypto = "aes";
    /**
    * 实现
     */
    private RpcType type = RpcType.DUBBO;
    /**
    * 协议
     */
    private List<RpcProtocolConfig> protocols;
    /**
    * 应用
     */
    private String applicationName = "${spring.application.name:app}";
    /**
    * 消费者
     */
    private RpcConsumerConfig consumer = new RpcConsumerConfig();
    /**
    * 注册器
     */
    private List<RpcRegistryConfig> registry;

    /**
    * 获取Registry
    *
    * @return 获取registry的结果
     */
    public List<RpcRegistryConfig> getRegistry() {
        if(null == registry) {
            return Collections.emptyList();
        }
        return registry;
    }

    /**
    * 获取协议
    *
    * @return 获取协议的结果
     */
    public List<RpcProtocolConfig> getProtocols() {
        if(null == protocols) {
            return Collections.emptyList();
        }
        return protocols;
    }

    /**
    * 获取 RPC 类型
    *
    * @return RPC 类型
     */
    public RpcType getType() {
        return type;
    }

    /**
    * 是否启用
    *
    * @return 是否启用
     */
    public boolean isEnable() {
        return enable;
    }

    /**
    * 设置是否启用
    *
    * @param enable 是否启用
     */
    public void setEnable(boolean enable) {
        this.enable = enable;
    }

    /**
    * 获取消费者配置
    *
    * @return 消费者配置
     */
    public RpcConsumerConfig getConsumer() {
        return consumer;
    }

    /**
    * 设置消费者配置
    *
    * @param consumer 消费者配置
     */
    public void setConsumer(RpcConsumerConfig consumer) {
        this.consumer = consumer;
    }

    /**
    * 获取应用名称
    *
    * @return 应用名称
     */
    public String getApplicationName() {
        return applicationName;
    }

    /**
    * 设置应用名称
    *
    * @param applicationName 应用名称
     */
    public void setApplicationName(String applicationName) {
        this.applicationName = applicationName;
    }

    /**
    * 获取扫描包
    *
    * @return 扫描包
     */
    public Set<String> getScan() {
        return scan;
    }

    /**
    * 设置扫描包
    *
    * @param scan 扫描包
     */
    public void setScan(Set<String> scan) {
        this.scan = scan;
    }
}
