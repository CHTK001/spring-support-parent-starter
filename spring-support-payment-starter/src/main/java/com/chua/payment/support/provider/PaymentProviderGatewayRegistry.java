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
 * 支付 提供者 SPI 注册中心，管理各支付渠道网关实例并提供获取入口。
 *
 * @author CH
 * @since 4.0.0.50
 */
@Component
public class PaymentProviderGatewayRegistry {

    /** 阿里云_alipay_gateway */
    private static final String ALIYUN_ALIPAY_GATEWAY = "com.chua.starter.aliyun.support.payment.AliyunAlipayGateway";
    /** 腾讯云_wechat_薪酬_gateway */
    private static final String TENCENT_WECHAT_PAY_GATEWAY = "com.chua.starter.tencent.support.payment.TencentWechatPayGateway";

    /** 属性 */
    private final PaymentProviderProperties properties;

    /**
     * 创建 薪酬提供者gatewayregistry 实例
     * @param properties 属性
     */
    public PaymentProviderGatewayRegistry(PaymentProviderProperties properties) {
        this.properties = properties;
    }

    /**
     * aliyunalipaygateway
     *
     * @return aliyunAlipayGateway的结果
     */
    public AliyunAlipayGateway aliyunAlipayGateway() {
        return aliyunAlipayGateway(null);
    }

    /**
     * tencentwechat薪酬gateway
     *
     * @return tencentwechat薪酬gateway的结果
     */
    public TencentWechatPayGateway tencentWechatPayGateway() {
        return tencentWechatPayGateway(null);
    }

    /**
     * aliyunalipaygateway
     *
     * @param extension 延伸
     * @return aliyunAlipayGateway的结果
     */
    public AliyunAlipayGateway aliyunAlipayGateway(String extension) {
        return required(ALIYUN_ALIPAY_GATEWAY, resolveAliyunExtension(extension), "Aliyun Alipay");
    }

    /**
     * tencentwechat薪酬gateway
     *
     * @param extension 延伸
     * @return tencentwechat薪酬gateway的结果
     */
    public TencentWechatPayGateway tencentWechatPayGateway(String extension) {
        return required(TENCENT_WECHAT_PAY_GATEWAY, resolveWechatExtension(extension), "Tencent Wechat Pay");
    }

    /**
     * 可用阿里云延伸
     *
     * @return 可用aliyun延伸的结果
     */
    public Set<String> availableAliyunExtensions() {
        return availableExtensions(ALIYUN_ALIPAY_GATEWAY);
    }

    /**
     * 可用wechat延伸
     *
     * @return 可用wechat延伸的结果
     */
    public Set<String> availableWechatExtensions() {
        return availableExtensions(TENCENT_WECHAT_PAY_GATEWAY);
    }

    /**
     * 解析阿里云延伸
     *
     * @param extension 延伸
     * @return resolvealiyun延伸的结果
     */
    public String resolveAliyunExtension(String extension) {
        return normalizeExtension(extension, properties.resolveForChannelType("ALIPAY"));
    }

    /**
     * 解析wechat延伸
     *
     * @param extension 延伸
     * @return resolvewechat延伸的结果
     */
    public String resolveWechatExtension(String extension) {
        return normalizeExtension(extension, properties.resolveForChannelType("WECHAT"));
    }

    /**
     * 默认阿里云延伸
     *
     * @return 默认aliyun延伸的结果
     */
    public String defaultAliyunExtension() {
        return properties.resolveForChannelType("ALIPAY");
    }

    /**
     * 默认wechat延伸
     *
     * @return 默认wechat延伸的结果
     */
    public String defaultWechatExtension() {
        return properties.resolveForChannelType("WECHAT");
    }

    /**
     * normalize延伸
     *
     * @param extensionName 延伸名称
     * @param fallback 降级
     * @return normalize延伸的结果
     */
    private String normalizeExtension(String extensionName, String fallback) {
        if (extensionName == null || extensionName.isBlank()) {
            return fallback;
        }
        return extensionName;
    }

    /**
     * 可用延伸
     *
     * @param typeName 类型名称
     * @return 可用延伸的结果
     */
    private Set<String> availableExtensions(String typeName) {
        Class<Object> type = loadType(typeName, false, typeName);
        if (type == null) {
            return Set.of();
        }
        return ServiceProvider.of(type).getExtensions();
    }

    /**
     * Required
     *
     * @param typeName 类型名称
     * @param extensionName 延伸名称
     * @param providerName 提供者名称
     * @return required的结果
     */
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
    /**
     * 加载类型
     *
     * @param typeName 类型名称
     * @param required required
     * @param providerName 提供者名称
     * @return 加载类型的结果
     */
    private <T> Class<T> loadType(String typeName, boolean required, String providerName) {
        try {
            ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
            if (classLoader == null) {
                classLoader = PaymentProviderGatewayRegistry.class.getClassLoader();
            }
            return (Class<T>) ReflectUtils.forName(typeName, classLoader);
        } catch (LinkageError e) {
            if (required) {
                /**
                 * 薪酬异常。
                 * @param " "
                 * @return 薪酬异常的结果
                 */
                throw new PaymentException("未找到支付 provider 依赖: " + providerName + " (" + typeName + ")", e);
            }
            return null;
        }
    }
}

