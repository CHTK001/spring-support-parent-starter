package com.chua.payment.support.provider;

import com.chua.common.support.core.spi.ServiceProvider;
import com.chua.payment.support.configuration.PaymentProviderProperties;
import com.chua.payment.support.exception.PaymentException;
import com.chua.starter.aliyun.support.payment.AliyunAlipayGateway;
import com.chua.starter.tencent.support.payment.TencentWechatPayGateway;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * 支付 provider SPI 注册中心
 */
@Component
public class PaymentProviderGatewayRegistry {

    private final PaymentProviderProperties properties;

    public PaymentProviderGatewayRegistry(PaymentProviderProperties properties) {
        this.properties = properties;
    }

    public AliyunAlipayGateway aliyunAlipayGateway() {
        return aliyunAlipayGateway(null);
    }

    public TencentWechatPayGateway tencentWechatPayGateway() {
        return tencentWechatPayGateway(null);
    }

    public AliyunAlipayGateway aliyunAlipayGateway(String extension) {
        return required(AliyunAlipayGateway.class, resolveAliyunExtension(extension), "Aliyun Alipay");
    }

    public TencentWechatPayGateway tencentWechatPayGateway(String extension) {
        return required(TencentWechatPayGateway.class, resolveWechatExtension(extension), "Tencent Wechat Pay");
    }

    public String resolveAliyunExtension(String extension) {
        return normalizeExtension(extension, properties.resolveForChannelType("ALIPAY"));
    }

    public String resolveWechatExtension(String extension) {
        return normalizeExtension(extension, properties.resolveForChannelType("WECHAT"));
    }

    public String defaultAliyunExtension() {
        return properties.resolveForChannelType("ALIPAY");
    }

    public String defaultWechatExtension() {
        return properties.resolveForChannelType("WECHAT");
    }

    private String normalizeExtension(String extensionName, String fallback) {
        if (extensionName == null || extensionName.isBlank()) {
            return fallback;
        }
        return extensionName;
    }

    private <T> T required(Class<T> type, String extensionName, String providerName) {
        String targetExtension = normalizeExtension(extensionName, properties.getDefaultSpi());
        ServiceProvider<T> provider = ServiceProvider.of(type);
        T extension = provider.getExtension(targetExtension);
        Set<String> extensions = provider.getExtensions();
        if (extension == null && !"default".equalsIgnoreCase(targetExtension)) {
            throw new PaymentException("未找到支付 provider SPI 实现: " + providerName
                    + " [" + targetExtension + "] (" + type.getName() + "), 可选实现: " + extensions);
        }
        if (extension == null) {
            throw new PaymentException("未找到支付 provider SPI 默认实现: " + providerName
                    + " (" + type.getName() + "), 可选实现: " + extensions);
        }
        return extension;
    }
}
