package com.chua.payment.support.configuration;

import com.chua.payment.support.channel.support.AbstractMerchantPaymentChannel;
import com.chua.payment.support.enums.PaymentChannelType;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 支付 provider SPI 默认配置
 */
@Component
@ConfigurationProperties(prefix = "plugin.payment.provider")
public class PaymentProviderProperties {

    private String defaultSpi = AbstractMerchantPaymentChannel.DEFAULT_PROVIDER_SPI;
    private String alipaySpi = AbstractMerchantPaymentChannel.DEFAULT_PROVIDER_SPI;
    private String wechatSpi = AbstractMerchantPaymentChannel.DEFAULT_PROVIDER_SPI;

    public String getDefaultSpi() {
        return defaultSpi;
    }

    public void setDefaultSpi(String defaultSpi) {
        this.defaultSpi = defaultSpi;
    }

    public String getAlipaySpi() {
        return alipaySpi;
    }

    public void setAlipaySpi(String alipaySpi) {
        this.alipaySpi = alipaySpi;
    }

    public String getWechatSpi() {
        return wechatSpi;
    }

    public void setWechatSpi(String wechatSpi) {
        this.wechatSpi = wechatSpi;
    }

    public String resolveForChannelType(String channelType) {
        String fallback = StringUtils.hasText(defaultSpi)
                ? defaultSpi
                : AbstractMerchantPaymentChannel.DEFAULT_PROVIDER_SPI;
        if (!StringUtils.hasText(channelType)) {
            return fallback;
        }
        if (PaymentChannelType.ALIPAY.getCode().equalsIgnoreCase(channelType)) {
            return StringUtils.hasText(alipaySpi) ? alipaySpi : fallback;
        }
        if (PaymentChannelType.WECHAT.getCode().equalsIgnoreCase(channelType)) {
            return StringUtils.hasText(wechatSpi) ? wechatSpi : fallback;
        }
        return fallback;
    }
}
