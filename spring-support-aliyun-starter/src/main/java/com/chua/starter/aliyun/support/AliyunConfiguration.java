package com.chua.starter.aliyun.support;

import com.alipay.api.AlipayClient;
import com.chua.starter.aliyun.support.factory.AliyunAlipayClientFactory;
import com.chua.starter.aliyun.support.properties.AliyunAlipayProperties;
import com.chua.starter.common.support.serviceinfo.ServiceInfoContributor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * 阿里云配置
 *
 * @since 2026-03-19
 * @author CH
 */
@Slf4j
@EnableConfigurationProperties(AliyunAlipayProperties.class)
public class AliyunConfiguration {

    @Bean
    @ConditionalOnMissingBean
    /** AliyunAlipayClientFactory */
    public AliyunAlipayClientFactory aliyunAlipayClientFactory() {
        return new AliyunAlipayClientFactory();
    }

    @Bean
    @ConditionalOnProperty(prefix = "plugin.aliyun.alipay", name = "enabled", havingValue = "true")
    /**
     * AlipayClient
     * @param properties properties
     * @param factory factory
     */
    public AlipayClient alipayClient(AliyunAlipayProperties properties,
                                     AliyunAlipayClientFactory factory) {
        log.info("[阿里云配置][支付宝] 加载配置");
        log.info("[阿里云配置][支付宝] appId => {}", properties.getAppId());
        log.info("[阿里云配置][支付宝] serverUrl => {}", properties.resolveServerUrl());
        return factory.createClient(properties);
    }

    @Bean
    /** AliyunServiceInfoContributor */
    public ServiceInfoContributor aliyunServiceInfoContributor() {
        return registry -> {
            registry.registerFlag("aliyun", "阿里云", true, "阿里云服务模块");
        };
    }
}
