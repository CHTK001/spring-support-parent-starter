package com.chua.payment.support.service;

import com.chua.starter.tencent.support.payment.dto.TencentWechatPayScoreNotifyPayload;

/**
 * 微信支付分回调服务
 */
public interface WechatPayScoreNotifyService {

    TencentWechatPayScoreNotifyPayload handleNotify(Long channelId,
                                                    String outOrderNo,
                                                    String serialNumber,
                                                    String timestamp,
                                                    String nonce,
                                                    String signature,
                                                    String signType,
                                                    String body);
}
