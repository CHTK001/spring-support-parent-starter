package com.chua.payment.support.configuration;

import com.chua.payment.support.channel.support.AbstractMerchantPaymentChannel;
import com.chua.payment.support.enums.PaymentChannelType;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 支付提供者 SPI 默认配置，管理各渠道网关对应的 SPI 实现标识。
 *
 * <p>配置前缀为 {@code plugin.payment.provider}。</p>
 *
 * @author CH
 * @since 4.0.0.50
 */
@Component
@ConfigurationProperties(prefix = "plugin.payment.provider")
public class PaymentProviderProperties {

    /**
     * 默认 SPI，未显式配置各渠道时的兜底实现标识。
     */
    private String defaultSpi = AbstractMerchantPaymentChannel.DEFAULT_PROVIDER_SPI;

    /**
     * 支付宝渠道的 SPI 实现标识。
     */
    private String alipaySpi = AbstractMerchantPaymentChannel.DEFAULT_PROVIDER_SPI;

    /**
     * 微信渠道的 SPI 实现标识。
     */
    private String wechatSpi = AbstractMerchantPaymentChannel.DEFAULT_PROVIDER_SPI;

    /**
     * 易支付渠道的 SPI 实现标识。
     */
    private String epaySpi = "epay";

    /**
     * 获取默认 SPI。
     *
     * @return 默认 SPI 的值
     */
    public String getDefaultSpi() {
        return defaultSpi;
    }

    /**
     * 设置默认 SPI。
     *
     * @param defaultSpi 默认 SPI
     */
    public void setDefaultSpi(String defaultSpi) {
        this.defaultSpi = defaultSpi;
    }

    /**
     * 获取支付宝渠道 SPI。
     *
     * @return 支付宝渠道 SPI 的值
     */
    public String getAlipaySpi() {
        return alipaySpi;
    }

    /**
     * 设置支付宝渠道 SPI。
     *
     * @param alipaySpi 支付宝渠道 SPI
     */
    public void setAlipaySpi(String alipaySpi) {
        this.alipaySpi = alipaySpi;
    }

    /**
     * 获取微信渠道 SPI。
     *
     * @return 微信渠道 SPI 的值
     */
    public String getWechatSpi() {
        return wechatSpi;
    }

    /**
     * 设置微信渠道 SPI。
     *
     * @param wechatSpi 微信渠道 SPI
     */
    public void setWechatSpi(String wechatSpi) {
        this.wechatSpi = wechatSpi;
    }

    /**
     * 获取易支付渠道 SPI。
     *
     * @return 易支付渠道 SPI 的值
     */
    public String getEpaySpi() {
        return epaySpi;
    }

    /**
     * 设置易支付渠道 SPI。
     *
     * @param epaySpi 易支付渠道 SPI
     */
    public void setEpaySpi(String epaySpi) {
        this.epaySpi = epaySpi;
    }

    /**
     * 按渠道类型解析对应的 SPI 实现标识。
     *
     * <p>渠道未配置专属 SPI 或渠道类型为空时，回退到 {@link #getDefaultSpi()}；
     * 默认值同样为空时使用 {@link AbstractMerchantPaymentChannel#DEFAULT_PROVIDER_SPI}。</p>
     *
     * @param channelType 渠道类型
     * @return 解析出的渠道 SPI 实现标识
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
