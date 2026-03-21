package com.chua.payment.support.provider;

import com.chua.common.support.core.spi.ServiceProvider;
import com.chua.payment.support.configuration.PaymentProviderProperties;
import com.chua.payment.support.exception.PaymentException;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * 支付 provider SPI 注册中心
 */
@Component
public class PaymentProviderGatewayRegistry {

    private static final String ALIYUN_ALIPAY_GATEWAY = "com.chua.starter.aliyun.support.payment.AliyunAlipayGateway";
    private static final String TENCENT_WECHAT_PAY_GATEWAY = "com.chua.starter.tencent.support.payment.TencentWechatPayGateway";

    private final PaymentProviderProperties properties;

    public PaymentProviderGatewayRegistry(PaymentProviderProperties properties) {
        this.properties = properties;
    }

    public <T> T aliyunAlipayGateway() {
        return aliyunAlipayGateway(null);
    }

    public <T> T tencentWechatPayGateway() {
        return tencentWechatPayGateway(null);
    }

    public <T> T aliyunAlipayGateway(String extension) {
        return required(ALIYUN_ALIPAY_GATEWAY, resolveAliyunExtension(extension), "Aliyun Alipay");
    }

    public <T> T tencentWechatPayGateway(String extension) {
        return required(TENCENT_WECHAT_PAY_GATEWAY, resolveWechatExtension(extension), "Tencent Wechat Pay");
    }

    public Set<String> availableAliyunExtensions() {
        return availableExtensions(ALIYUN_ALIPAY_GATEWAY);
    }

    public Set<String> availableWechatExtensions() {
        return availableExtensions(TENCENT_WECHAT_PAY_GATEWAY);
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

    private Set<String> availableExtensions(String typeName) {
        Class<Object> type = loadType(typeName, false, typeName);
        if (type == null) {
            return Set.of();
        }
        return ServiceProvider.of(type).getExtensions();
    }

    private <T> T required(String typeName, String extensionName, String providerName) {
        String targetExtension = normalizeExtension(extensionName, properties.getDefaultSpi());
        Class<T> type = loadType(typeName, true, providerName);
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

    @SuppressWarnings("unchecked")
    private <T> Class<T> loadType(String typeName, boolean required, String providerName) {
        try {
            ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
            if (classLoader == null) {
                classLoader = PaymentProviderGatewayRegistry.class.getClassLoader();
            }
            return (Class<T>) Class.forName(typeName, false, classLoader);
        } catch (ClassNotFoundException | LinkageError e) {
            if (required) {
                throw new PaymentException("未找到支付 provider 依赖: " + providerName + " (" + typeName + ")", e);
            }
            return null;
        }
    }
}
