package com.chua.starter.aliyun.support.factory;

import com.alipay.api.AlipayClient;
import com.alipay.api.AlipayConfig;
import com.alipay.api.AlipayApiException;
import com.alipay.api.DefaultAlipayClient;
import com.chua.starter.aliyun.support.properties.AliyunAlipayProperties;

/**
* 支付宝客户端工厂
*
* @since 2026-03-19
* @author CH
 */
public class AliyunAlipayClientFactory {

    /**
    * 创建客户端
    *
    * @param properties 属性
    * @return 创建客户端的结果
    */
    public AlipayClient createClient(AliyunAlipayProperties properties) {
        return createClient(createConfig(properties));
    }

    /**
    * 创建客户端
    *
    * @param config 配置
    * @return 创建客户端的结果
    */
    public AlipayClient createClient(AlipayConfig config) {
        try {
            /**
            * 默认alipay客户端。
            * @param config 配置
            * @return 默认alipay客户端的结果
            */
            return new DefaultAlipayClient(config);
        } catch (AlipayApiException e) {
            /**
            * illegal状态异常。
            * @param "创建支付宝客户端失败" "创建支付宝客户端失败"
            * @param e e
            * @return illegal状态异常的结果
            */
            throw new IllegalStateException("创建支付宝客户端失败", e);
        }
    }

    /**
    * 创建配置
    *
    * @param properties 属性
    * @return 创建配置的结果
    */
    public AlipayConfig createConfig(AliyunAlipayProperties properties) {
        AlipayConfig config = new AlipayConfig();
        config.setServerUrl(properties.resolveServerUrl());
        config.setAppId(properties.getAppId());
        config.setPrivateKey(properties.getPrivateKey());
        config.setAlipayPublicKey(properties.getAlipayPublicKey());
        config.setFormat(properties.getFormat());
        config.setCharset(properties.getCharset());
        config.setSignType(properties.getSignType());
        if (properties.getConnectTimeout() != null) {
            config.setConnectTimeout(properties.getConnectTimeout());
        }
        if (properties.getReadTimeout() != null) {
            config.setReadTimeout(properties.getReadTimeout());
        }
        if (properties.getMaxIdleConnections() != null) {
            config.setMaxIdleConnections(properties.getMaxIdleConnections());
        }
        if (properties.getKeepAliveDuration() != null) {
            config.setKeepAliveDuration(properties.getKeepAliveDuration());
        }
        return config;
    }
}
