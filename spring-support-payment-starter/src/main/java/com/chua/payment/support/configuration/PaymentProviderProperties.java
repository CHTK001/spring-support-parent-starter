package com.chua.payment.support.configuration;

import com.chua.payment.support.channel.support.AbstractMerchantPaymentChannel;
import com.chua.payment.support.enums.PaymentChannelType;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 支付 provider SPI 默认配置
 * @author CH
 */
@Component
@ConfigurationProperties(prefix = "plugin.payment.provider")
public class PaymentProviderProperties {

    /** 默认SPI */
    private String defaultSpi = AbstractMerchantPaymentChannel.DEFAULT_PROVIDER_SPI;
    /** AlipaySPI */
    private String alipaySpi = AbstractMerchantPaymentChannel.DEFAULT_PROVIDER_SPI;
    /** WechatSPI */
    private String wechatSpi = AbstractMerchantPaymentChannel.DEFAULT_PROVIDER_SPI;
    /** EpaySPI */
    private String epaySpi = "epay";

    /** 获取DefaultSpi */
    public String getDefaultSpi() {
        return defaultSpi;
    }

    /** 设置DefaultSpi */
    public void setDefaultSpi(String defaultSpi) {
        this.defaultSpi = defaultSpi;
    }

    /** 获取AlipaySpi */
    public String getAlipaySpi() {
        return alipaySpi;
    }

    /** 设置AlipaySpi */
    public void setAlipaySpi(String alipaySpi) {
        this.alipaySpi = alipaySpi;
    }

    /** 获取WechatSpi */
    public String getWechatSpi() {
        return wechatSpi;
    }

    /** 设置WechatSpi */
    public void setWechatSpi(String wechatSpi) {
        this.wechatSpi = wechatSpi;
    }

    /** 获取EpaySpi */
    public String getEpaySpi() {
        return epaySpi;
    }

    /** 设置EpaySpi */
    public void setEpaySpi(String epaySpi) {
        this.epaySpi = epaySpi;
    }

    /** 解析ForChannelType */
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
        if (PaymentChannelType.EPAY.getCode().equalsIgnoreCase(channelType)) {
            return StringUtils.hasText(epaySpi) ? epaySpi : "epay";
        }
        return fallback;
    }
}

