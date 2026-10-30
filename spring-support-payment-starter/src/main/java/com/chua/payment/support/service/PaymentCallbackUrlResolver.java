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

    /**
     * 支付全局配置服务
     */
    private final PaymentGlobalConfigService paymentGlobalConfigService;

    /**
     * 解析支付通知地址
     *
     * @param requestNotifyUrl 请求方通知地址
     * @param channel 渠道
     * @param merchant 商户
     * @param orderNo 订单号
     * @return 解析出的支付通知地址
     */
    public String resolvePayNotifyUrl(String requestNotifyUrl, MerchantChannel channel, Merchant merchant, String orderNo) {
        return firstNonBlank(
                requestNotifyUrl,
                channel != null ? channel.getNotifyUrl() : null,
                merchant != null ? merchant.getDefaultNotifyUrl() : null,
                defaultPayNotifyUrl(channel, merchant, orderNo));
    }

    /**
     * 解析退款通知地址
     *
     * @param channel 渠道
     * @param merchantId 商户标识
     * @param refundNo 退款单号
     * @param fallbackNotifyUrl 降级通知地址
     * @return 解析出的退款通知地址
     */
    public String resolveRefundNotifyUrl(MerchantChannel channel, Long merchantId, String refundNo, String fallbackNotifyUrl) {
        return firstNonBlank(defaultRefundNotifyUrl(channel, merchantId, refundNo), fallbackNotifyUrl);
    }

    /**
     * 默认支付通知地址
     *
     * @param channel 渠道
     * @param merchant 商户
     * @param orderNo 订单号
     * @return 默认支付通知地址
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
     * 默认退款通知地址
     *
     * @param channel 渠道
     * @param merchantId 商户标识
     * @param refundNo 退款单号
     * @return 默认退款通知地址
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
     * 默认钱包通知地址
     *
     * @param orderType 订单类型
     * @param orderNo 订单号
     * @return 默认钱包通知地址
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
     * 默认微信支付分（先享后付）通知地址
     *
     * @param channelId 渠道标识
     * @param outOrderNo 出订单号
     * @return 默认微信支付分通知地址
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
     * 规范化基础地址：去掉末尾多余的斜杠
     * @return 返回值
    */
    private String normalizedBaseUrl() {
        String baseUrl = paymentGlobalConfigService.getConfigEntity().getPaymentNotifyBaseUrl();
        if (!StringUtils.hasText(baseUrl)) {
            return null;
        }
        return baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }

    /**
     * 返回第一个非空白值
     *
     * @param values 候选值
     * @return 第一个非空白值，全部为空时返回 null
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
     * 转大写
     *
     * @param value 值
     * @return 转大写后的值
     */
    private String upper(String value) {
        return value == null ? null : value.toUpperCase(Locale.ROOT);
    }
}
