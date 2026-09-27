package com.chua.payment.support.service;

import com.chua.starter.tencent.support.payment.dto.TencentWechatPayScoreNotifyPayload;
import com.chua.payment.support.dto.WechatPayScoreNotifyDTO;

/**
 * 微信支付分回调服务接口，定义微信支付分异步通知解析与处理契约。
 *
 * @author CH
 * @since 4.0.0.50
 */
public interface WechatPayScoreNotifyService {

    /**
     * 处理微信支付分通知。
     * @param dto dto
     * @return 处理结果应答报文
     */
    TencentWechatPayScoreNotifyPayload handleNotify(WechatPayScoreNotifyDTO dto);
}
