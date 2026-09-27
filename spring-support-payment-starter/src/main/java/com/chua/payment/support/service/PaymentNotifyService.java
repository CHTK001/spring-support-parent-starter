package com.chua.payment.support.service;

import com.chua.payment.support.dto.WechatPayNotifyDTO;
import com.chua.payment.support.dto.WechatRefundNotifyDTO;
import java.util.Map;

/**
 * 第三方支付异步通知服务接口，定义微信、支付宝等渠道的回调处理契约。
 *
 * @author CH
 * @since 4.0.0.50
 */
public interface PaymentNotifyService {

    /**
     * 处理微信支付通知。
     * @param dto dto
     */
    void handleWechatPayNotify(WechatPayNotifyDTO dto);

    /**
     * 处理微信退款通知。
     * @param dto dto
     */
    void handleWechatRefundNotify(WechatRefundNotifyDTO dto);

    /**
     * 处理支付宝支付通知。
     * @param channelId 渠道标识
     * @param orderNo 订单号
     * @param params 参数
     */
    void handleAlipayPayNotify(Long channelId, String orderNo, Map<String, String> params);
}
