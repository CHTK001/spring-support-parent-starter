package com.chua.payment.support.configuration;

import com.chua.payment.support.channel.support.AbstractMerchantPaymentChannel;
import com.chua.payment.support.enums.PaymentChannelType;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
   * 支付 提供者 SPI 默认配置，管理各渠道网关对应的 SPI 实现标识。
 *
 * @author CH
 * @since 4.0.0.50
 */
@Component
@ConfigurationProperties(prefix = "plugin.payment.provider")
public class PaymentProviderProperties {

    /** 默认SPI */
    private String defaultSpi = AbstractMerchantPaymentChannel.DEFAULT_PROVIDER_SPI;
    /** alipayspi */
    private String alipaySpi = AbstractMerchantPaymentChannel.DEFAULT_PROVIDER_SPI;
    /** wechatspi */
    private String wechatSpi = AbstractMerchantPaymentChannel.DEFAULT_PROVIDER_SPI;
    /** epayspi */
    private String epaySpi = "epay";

    /**
     * 获取默认spi
     *
     * @return 获取默认spi的结果
     */
    public String getDefaultSpi() {
        return defaultSpi;
    }

    /**
     * 设置默认spi
     *
     * @param defaultSpi 默认spi
     */
    public void setDefaultSpi(String defaultSpi) {
        this.defaultSpi = defaultSpi;
    }

    /**
     * 获取alipayspi
     *
     * @return 获取alipayspi的结果
     */
    public String getAlipaySpi() {
        return alipaySpi;
    }

    /**
     * 设置alipayspi
     *
     * @param alipaySpi alipayspi
     */
    public void setAlipaySpi(String alipaySpi) {
        this.alipaySpi = alipaySpi;
    }

    /**
     * 获取wechatspi
     *
     * @return 获取wechatspi的结果
     */
    public String getWechatSpi() {
        return wechatSpi;
    }

    /**
     * 设置wechatspi
     *
     * @param wechatSpi wechatspi
     */
    public void setWechatSpi(String wechatSpi) {
        this.wechatSpi = wechatSpi;
    }

    /**
     * 获取epayspi
     *
     * @return 获取epayspi的结果
     */
    public String getEpaySpi() {
        return epaySpi;
    }

    /**
     * 设置epayspi
     *
     * @param epaySpi epayspi
     */
    public void setEpaySpi(String epaySpi) {
        this.epaySpi = epaySpi;
    }

    /**
     * 解析for通道类型
     *
     * @param channelType 通道类型
     * @return resolvefor通道类型的结果
     */
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

