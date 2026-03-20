package com.chua.payment.support.service;

import java.util.Map;

/**
 * 第三方支付异步通知服务
 */
public interface PaymentNotifyService {

    void handleWechatPayNotify(Long channelId,
                               String serialNumber,
                               String timestamp,
                               String nonce,
                               String signature,
                               String signType,
                               String body);

    void handleWechatRefundNotify(Long channelId,
                                  String serialNumber,
                                  String timestamp,
                                  String nonce,
                                  String signature,
                                  String signType,
                                  String body);

    void handleAlipayPayNotify(Long channelId, Map<String, String> params);
}
