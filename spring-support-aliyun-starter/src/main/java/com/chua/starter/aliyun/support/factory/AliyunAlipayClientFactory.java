package com.chua.starter.aliyun.support.factory;

import com.alipay.api.AlipayClient;
import com.alipay.api.AlipayConfig;
import com.alipay.api.AlipayApiException;
import com.alipay.api.DefaultAlipayClient;
import com.chua.starter.aliyun.support.properties.AliyunAlipayProperties;

/**
 * 支付宝客户端工厂
 *
 * @author CH
 * @since 2026-03-19
 */
public class AliyunAlipayClientFactory {

    public AlipayClient createClient(AliyunAlipayProperties properties) {
        return createClient(createConfig(properties));
    }

    public AlipayClient createClient(AlipayConfig config) {
        try {
            return new DefaultAlipayClient(config);
        } catch (AlipayApiException e) {
            throw new IllegalStateException("创建支付宝客户端失败", e);
        }
    }

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
