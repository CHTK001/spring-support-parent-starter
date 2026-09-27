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
 * 支付提供方 SPI 注册中心：管理各支付通道网关实例并提供获取入口
 *
 * @author CH
 * @since 4.0.0.50
 */
@Component
public class PaymentProviderGatewayRegistry {

    /**
     * 阿里云 alipay 网关全限定类名
     */
    private static final String ALIYUN_ALIPAY_GATEWAY = "com.chua.starter.aliyun.support.payment.AliyunAlipayGateway";
    /**
     * 腾讯云 wechat 支付网关全限定类名
     */
    private static final String TENCENT_WECHAT_PAY_GATEWAY = "com.chua.starter.tencent.support.payment.TencentWechatPayGateway";

    /**
     * 提供方配置属性
     */
    private final PaymentProviderProperties properties;

    /**
     * 创建支付提供方网关注册中心实例
     * @param properties 提供方配置属性
     */
    public PaymentProviderGatewayRegistry(PaymentProviderProperties properties) {
        this.properties = properties;
    }

    /**
     * 获取阿里云支付宝网关
     *
     * @return 阿里云支付宝网关
     */
    public AliyunAlipayGateway aliyunAlipayGateway() {
        return aliyunAlipayGateway(null);
    }

    /**
     * 获取腾讯微信支付网关
     *
     * @return 腾讯微信支付网关
     */
    public TencentWechatPayGateway tencentWechatPayGateway() {
        return tencentWechatPayGateway(null);
    }

    /**
     * 获取阿里云支付宝网关
     *
     * @param extension 扩展名
     * @return 阿里云支付宝网关
     */
    public AliyunAlipayGateway aliyunAlipayGateway(String extension) {
        return required(ALIYUN_ALIPAY_GATEWAY, resolveAliyunExtension(extension), "Aliyun Alipay");
    }

    /**
     * 获取腾讯微信支付网关
     *
     * @param extension 扩展名
     * @return 腾讯微信支付网关
     */
    public TencentWechatPayGateway tencentWechatPayGateway(String extension) {
        return required(TENCENT_WECHAT_PAY_GATEWAY, resolveWechatExtension(extension), "Tencent Wechat Pay");
    }

    /**
     * 可用的阿里云扩展名
     *
     * @return 可用的阿里云扩展名
     */
    public Set<String> availableAliyunExtensions() {
        return availableExtensions(ALIYUN_ALIPAY_GATEWAY);
    }

    /**
     * 可用的微信扩展名
     *
     * @return 可用的微信扩展名
     */
    public Set<String> availableWechatExtensions() {
        return availableExtensions(TENCENT_WECHAT_PAY_GATEWAY);
    }

    /**
     * 解析阿里云扩展名
     *
     * @param extension 扩展名
     * @return 解析后的阿里云扩展名
     */
    public String resolveAliyunExtension(String extension) {
        return normalizeExtension(extension, properties.resolveForChannelType("ALIPAY"));
    }

    /**
     * 解析微信扩展名
     *
     * @param extension 扩展名
     * @return 解析后的微信扩展名
     */
    public String resolveWechatExtension(String extension) {
        return normalizeExtension(extension, properties.resolveForChannelType("WECHAT"));
    }

    /**
     * 默认阿里云扩展名
     *
     * @return 默认阿里云扩展名
     */
    public String defaultAliyunExtension() {
        return properties.resolveForChannelType("ALIPAY");
    }

    /**
     * 默认微信扩展名
     *
     * @return 默认微信扩展名
     */
    public String defaultWechatExtension() {
        return properties.resolveForChannelType("WECHAT");
    }

    /**
     * 归一化扩展名
     *
     * @param extensionName 扩展名
     * @param fallback 降级默认值
     * @return 归一化后的扩展名
     */
    private String normalizeExtension(String extensionName, String fallback) {
        if (extensionName == null || extensionName.isBlank()) {
            return fallback;
        }
        return extensionName;
    }

    /**
     * 获取可用扩展名
     *
     * @param typeName 类型全限定类名
     * @return 可用扩展名集合
     */
    private Set<String> availableExtensions(String typeName) {
        Class<Object> type = loadType(typeName, false, typeName);
        if (type == null) {
            return Set.of();
        }
        return ServiceProvider.of(type).getExtensions();
    }

    /**
     * 获取必需的 SPI 实现
     *
     * @param typeName 类型全限定类名
     * @param extensionName 扩展名
     * @param providerName 提供方名称
     * @return SPI 实现实例
     */
    private <T> T required(String typeName, String extensionName, String providerName) {
        String targetExtension = normalizeExtension(extensionName, properties.getDefaultSpi());
        Class<T> type = loadType(typeName, true, providerName);
        ServiceProvider<T> provider = ServiceProvider.of(type);
        T extension = provider.getExtension(targetExtension);
        Set<String> extensions = provider.getExtensions();
        if (extension == null && !"default".equalsIgnoreCase(targetExtension)) {
            throw new PaymentException("未找到指定的 provider SPI 实现: " + providerName
                    + " [" + targetExtension + "] (" + type.getName() + "), 可选实现: " + extensions);
        }
        if (extension == null) {
            throw new PaymentException("未找到指定的 provider SPI 默认实现: " + providerName
                    + " (" + type.getName() + "), 可选实现: " + extensions);
        }
        return extension;
    }

    @SuppressWarnings("unchecked")
    /**
     * 加载类型
     *
     * @param typeName 类型全限定类名
     * @param required 是否必需
     * @param providerName 提供方名称
     * @return 加载到的类型
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
                throw new PaymentException("未找到指定的 provider 依赖: " + providerName + " (" + typeName + ")", e);
            }
            return null;
        }
    }
}
