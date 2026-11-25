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
 * <p>完整 YAML 配置示例（各项均可省略，省略时使用下方字段默认值）：</p>
 * <pre>{@code
 * plugin:
 *   payment:
 *     provider:
 *       default-spi: default    # 全局兜底 SPI，默认 default
 *       alipay-spi: default     # 支付宝渠道 SPI，默认 default，为空时回落 default-spi
 *       wechat-spi: wechat-pay  # 微信渠道 SPI，默认 default，为空时回落 default-spi
 *       epay-spi: epay          # 易支付渠道 SPI，默认 epay，为空时仍按 epay 处理
 * }</pre>
 *
 * <p>渠道级 {@code extConfig.providerSpi}（或 {@code providerExtension}/{@code spi}）优先级高于本配置，
 * 解析逻辑见 {@link AbstractMerchantPaymentChannel}。</p>
 *
 * @author CH
 * @since 4.0.0.50
 */
@Component
@ConfigurationProperties(prefix = "plugin.payment.provider")
public class PaymentProviderProperties {

    /**
     * 默认 SPI，未显式配置各渠道时的兜底实现标识。
     *
     * <p>含义：渠道未配置专属 SPI（渠道级 extConfig 也未指定）时使用的 provider SPI 实现名。</p>
     * <p>默认值：{@code default}（{@link AbstractMerchantPaymentChannel#DEFAULT_PROVIDER_SPI}）；
     * 配置为空时解析阶段同样回落到 {@code default}。</p>
     * <p>YAML 示例：</p>
     * <pre>{@code
     * plugin:
     *   payment:
     *     provider:
     *       default-spi: default
     * }</pre>
     */
    private String defaultSpi = AbstractMerchantPaymentChannel.DEFAULT_PROVIDER_SPI;

    /**
     * 支付宝渠道的 SPI 实现标识。
     *
     * <p>含义：{@code PaymentChannelType.ALIPAY} 渠道（WEB/WAP/APP）解析网关 SPI 时使用的实现名。</p>
     * <p>默认值：{@code default}；配置为空时回落到 {@link #getDefaultSpi() defaultSpi}
     * （仍为空则回落 {@code default}）。</p>
     * <p>YAML 示例：</p>
     * <pre>{@code
     * plugin:
     *   payment:
     *     provider:
     *       alipay-spi: alipay
     * }</pre>
     */
    private String alipaySpi = AbstractMerchantPaymentChannel.DEFAULT_PROVIDER_SPI;

    /**
     * 微信渠道的 SPI 实现标识。
     *
     * <p>含义：{@code PaymentChannelType.WECHAT} 渠道（JSAPI/NATIVE/小程序/H5/APP）解析网关 SPI 时使用的实现名。</p>
     * <p>默认值：{@code default}；配置为空时回落到 {@link #getDefaultSpi() defaultSpi}
     * （仍为空则回落 {@code default}）。</p>
     * <p>YAML 示例：</p>
     * <pre>{@code
     * plugin:
     *   payment:
     *     provider:
     *       wechat-spi: wechat-pay
     * }</pre>
     */
    private String wechatSpi = AbstractMerchantPaymentChannel.DEFAULT_PROVIDER_SPI;

    /**
     * 易支付渠道的 SPI 实现标识。
     *
     * <p>含义：{@code PaymentChannelType.EPAY} 渠道解析网关 SPI 时使用的实现名。</p>
     * <p>默认值：{@code epay}；配置为空时解析阶段仍按 {@code epay} 处理
     * （见 {@link #resolveForChannelType(String)}）。</p>
     * <p>YAML 示例：</p>
     * <pre>{@code
     * plugin:
     *   payment:
     *     provider:
     *       epay-spi: epay
     * }</pre>
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
