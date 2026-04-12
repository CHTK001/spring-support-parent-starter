package com.chua.payment.support.service;

import com.chua.payment.support.entity.Merchant;
import com.chua.payment.support.entity.MerchantChannel;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Locale;

/**
 * 统一生成支付回调地址
 */
@Component
@RequiredArgsConstructor
public class PaymentCallbackUrlResolver {

    private final PaymentGlobalConfigService paymentGlobalConfigService;

    public String resolvePayNotifyUrl(String requestNotifyUrl, MerchantChannel channel, Merchant merchant, String orderNo) {
        return firstNonBlank(
                requestNotifyUrl,
                channel != null ? channel.getNotifyUrl() : null,
                merchant != null ? merchant.getDefaultNotifyUrl() : null,
                defaultPayNotifyUrl(channel, merchant, orderNo));
    }

    public String resolveRefundNotifyUrl(MerchantChannel channel, Long merchantId, String refundNo, String fallbackNotifyUrl) {
        return firstNonBlank(defaultRefundNotifyUrl(channel, merchantId, refundNo), fallbackNotifyUrl);
    }

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

    private String normalizedBaseUrl() {
        String baseUrl = paymentGlobalConfigService.getConfigEntity().getPaymentNotifyBaseUrl();
        if (!StringUtils.hasText(baseUrl)) {
            return null;
        }
        return baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }

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

    private String upper(String value) {
        return value == null ? null : value.toUpperCase(Locale.ROOT);
    }
}
