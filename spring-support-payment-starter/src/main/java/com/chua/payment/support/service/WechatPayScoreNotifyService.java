package com.chua.payment.support.service;

import com.chua.starter.tencent.support.payment.dto.TencentWechatPayScoreNotifyPayload;
import com.chua.payment.support.dto.WechatPayScoreNotifyDTO;

/**
 * 微信支付分回调服务接口，定义微信分异步通知解析与处理契约。
 *
 * @author CH
 * @since 4.0.0.50
 */
public interface WechatPayScoreNotifyService {

    TencentWechatPayScoreNotifyPayload handleNotify(WechatPayScoreNotifyDTO dto);
}

