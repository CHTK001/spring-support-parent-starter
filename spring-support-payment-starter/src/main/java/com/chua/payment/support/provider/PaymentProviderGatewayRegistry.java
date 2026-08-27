package com.chua.payment.support.provider;

import com.chua.common.support.reflection.ReflectUtils;
import com.chua.common.support.spi.ServiceProvider;
import com.chua.payment.support.configuration.PaymentProviderProperties;
import com.chua.payment.support.exception.PaymentException;
import com.chua.starter.aliyun.support.payment.AliyunAlipayGateway;
import com.chua.starter.tencent.support.payment.TencentWechatPayGateway;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * 支付 provider SPI 注册中心，管理各支付渠道网关实例并提供获取入口。
 *
 * @author CH
 * @since 4.0.0.50
 */
@Component
public class PaymentProviderGatewayRegistry {

    /** Aliyun_alipay_gateway */
    private static final String ALIYUN_ALIPAY_GATEWAY = "com.chua.starter.aliyun.support.payment.AliyunAlipayGateway";
    /** Tencent_wechat_pay_gateway */
    private static final String TENCENT_WECHAT_PAY_GATEWAY = "com.chua.starter.tencent.support.payment.TencentWechatPayGateway";

    /** 属性 */
    private final PaymentProviderProperties properties;

    /**
     * 创建 PaymentProviderGatewayRegistry 实例
     * @param properties properties
     */
    public PaymentProviderGatewayRegistry(PaymentProviderProperties properties) {
        this.properties = properties;
    }

    /** AliyunAlipayGateway */
    public AliyunAlipayGateway aliyunAlipayGateway() {
        return aliyunAlipayGateway(null);
    }

    /** TencentWechatPayGateway */
    public TencentWechatPayGateway tencentWechatPayGateway() {
        return tencentWechatPayGateway(null);
    }

    /** AliyunAlipayGateway */
    public AliyunAlipayGateway aliyunAlipayGateway(String extension) {
        return required(ALIYUN_ALIPAY_GATEWAY, resolveAliyunExtension(extension), "Aliyun Alipay");
    }

    /** TencentWechatPayGateway */
    public TencentWechatPayGateway tencentWechatPayGateway(String extension) {
        return required(TENCENT_WECHAT_PAY_GATEWAY, resolveWechatExtension(extension), "Tencent Wechat Pay");
    }

    /** AvailableAliyunExtensions */
    public Set<String> availableAliyunExtensions() {
        return availableExtensions(ALIYUN_ALIPAY_GATEWAY);
    }

    /** AvailableWechatExtensions */
    public Set<String> availableWechatExtensions() {
        return availableExtensions(TENCENT_WECHAT_PAY_GATEWAY);
    }

    /** 解析AliyunExtension */
    public String resolveAliyunExtension(String extension) {
        return normalizeExtension(extension, properties.resolveForChannelType("ALIPAY"));
    }

    /** 解析WechatExtension */
    public String resolveWechatExtension(String extension) {
        return normalizeExtension(extension, properties.resolveForChannelType("WECHAT"));
    }

    /** DefaultAliyunExtension */
    public String defaultAliyunExtension() {
        return properties.resolveForChannelType("ALIPAY");
    }

    /** DefaultWechatExtension */
    public String defaultWechatExtension() {
        return properties.resolveForChannelType("WECHAT");
    }

    /** NormalizeExtension */
    private String normalizeExtension(String extensionName, String fallback) {
        if (extensionName == null || extensionName.isBlank()) {
            return fallback;
        }
        return extensionName;
    }

    /** AvailableExtensions */
    private Set<String> availableExtensions(String typeName) {
        Class<Object> type = loadType(typeName, false, typeName);
        if (type == null) {
            return Set.of();
        }
        return ServiceProvider.of(type).getExtensions();
    }

    /** Required */
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
    /** 加载Type */
    private <T> Class<T> loadType(String typeName, boolean required, String providerName) {
        try {
            ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
            if (classLoader == null) {
                classLoader = PaymentProviderGatewayRegistry.class.getClassLoader();
            }
            return (Class<T>) ReflectUtils.forName(typeName, classLoader);
        } catch (LinkageError e) {
            if (required) {
                throw new PaymentException("未找到支付 provider 依赖: " + providerName + " (" + typeName + ")", e);
            }
            return null;
        }
    }
}

