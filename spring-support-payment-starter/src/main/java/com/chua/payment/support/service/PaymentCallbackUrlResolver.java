package com.chua.payment.support.service;

import com.chua.payment.support.entity.Merchant;
import com.chua.payment.support.entity.MerchantChannel;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Locale;

/**
 * 统一生成支付回调地址
 * @author CH
 * @since 4.0.0
 */
@Component
@RequiredArgsConstructor
public class PaymentCallbackUrlResolver {

    /** Payment全局配置服务 */
    private final PaymentGlobalConfigService paymentGlobalConfigService;

    /**
     * 解析薪酬通知Url
     *
     * @param requestNotifyUrl 请求通知url
     * @param channel 通道
     * @param merchant merchant
     * @param orderNo 订单no
     * @return resolve薪酬通知url的结果
     */
    public String resolvePayNotifyUrl(String requestNotifyUrl, MerchantChannel channel, Merchant merchant, String orderNo) {
        return firstNonBlank(
                requestNotifyUrl,
                channel != null ? channel.getNotifyUrl() : null,
                merchant != null ? merchant.getDefaultNotifyUrl() : null,
                defaultPayNotifyUrl(channel, merchant, orderNo));
    }

    /**
     * 解析退税通知Url
     *
     * @param channel 通道
     * @param merchantId merchantid
     * @param refundNo 退税no
     * @param fallbackNotifyUrl 降级通知url
     * @return resolve退税通知url的结果
     */
    public String resolveRefundNotifyUrl(MerchantChannel channel, Long merchantId, String refundNo, String fallbackNotifyUrl) {
        return firstNonBlank(defaultRefundNotifyUrl(channel, merchantId, refundNo), fallbackNotifyUrl);
    }

    /**
     * 默认薪酬通知Url
     *
     * @param channel 通道
     * @param merchant merchant
     * @param orderNo 订单no
     * @return 默认薪酬通知url的结果
     */
    public String defaultPayNotifyUrl(MerchantChannel channel, Merchant merchant, String orderNo) {
        if (channel == null || merchant == null || merchant.getId() == null || !StringUtils.hasText(orderNo)) {
            return null;
        }
        String baseUrl = normalizedBaseUrl();
        if (!StringUtils.hasText(baseUrl)) {
            return null;
        }
        String channelType = upper(channel.getChannelType());
        if ("WECHAT".equals(channelType) || "EPAY".equals(channelType)) {
            return baseUrl + "/api/notify/wechat/pay/" + orderNo + "/" + merchant.getId();
        }
        if ("ALIPAY".equals(channelType)) {
            return baseUrl + "/api/notify/alipay/pay/" + orderNo + "/" + merchant.getId();
        }
        return null;
    }

    /**
     * 默认退税通知Url
     *
     * @param channel 通道
     * @param merchantId merchantid
     * @param refundNo 退税no
     * @return 默认退税通知url的结果
     */
    public String defaultRefundNotifyUrl(MerchantChannel channel, Long merchantId, String refundNo) {
        if (channel == null || merchantId == null || !StringUtils.hasText(refundNo)) {
            return null;
        }
        String baseUrl = normalizedBaseUrl();
        if (!StringUtils.hasText(baseUrl)) {
            return null;
        }
        if ("WECHAT".equals(upper(channel.getChannelType())) || "EPAY".equals(upper(channel.getChannelType()))) {
            return baseUrl + "/api/notify/wechat/refund/" + refundNo + "/" + merchantId;
        }
        return null;
    }

    /**
     * 默认wallet通知Url
     *
     * @param orderType 订单类型
     * @param orderNo 订单no
     * @return 默认wallet通知url的结果
     */
    public String defaultWalletNotifyUrl(String orderType, String orderNo) {
        if (!StringUtils.hasText(orderType) || !StringUtils.hasText(orderNo)) {
            return null;
        }
        String baseUrl = normalizedBaseUrl();
        if (!StringUtils.hasText(baseUrl)) {
            return null;
        }
        return baseUrl
                + "/api/notify/wallet/"
                + orderType.trim().replace('-', '_').toLowerCase(Locale.ROOT)
                + "/"
                + orderNo;
    }

    /**
     * 默认wechat薪酬score通知Url
     *
     * @param channelId 通道标识
     * @param outOrderNo 出订单no
     * @return 默认wechat薪酬score通知url的结果
     */
    public String defaultWechatPayScoreNotifyUrl(Long channelId, String outOrderNo) {
        if (channelId == null || !StringUtils.hasText(outOrderNo)) {
            return null;
        }
        String baseUrl = normalizedBaseUrl();
        if (!StringUtils.hasText(baseUrl)) {
            return null;
        }
        return baseUrl + "/api/notify/wechat/payscore/" + channelId + "/" + outOrderNo;
    }

    /**
     * normalizedbaseurl
     *
     * @return normalizedBaseUrl的结果
     */
    private String normalizedBaseUrl() {
        String baseUrl = paymentGlobalConfigService.getConfigEntity().getPaymentNotifyBaseUrl();
        if (!StringUtils.hasText(baseUrl)) {
            return null;
        }
        return baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }

    /**
     * 第一个nonblank
     *
     * @param values 值
     * @return 第一个nonblank的结果
     */
    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return null;
    }

    /**
     * 大写
     *
     * @param value 值
     * @return 大写的结果
     */
    private String upper(String value) {
        return value == null ? null : value.toUpperCase(Locale.ROOT);
    }
}

