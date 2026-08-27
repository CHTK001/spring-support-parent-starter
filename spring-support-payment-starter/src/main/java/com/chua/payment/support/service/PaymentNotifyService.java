package com.chua.payment.support.service;

import com.chua.payment.support.dto.WechatPayNotifyDTO;
import com.chua.payment.support.dto.WechatRefundNotifyDTO;
import java.util.Map;

/**
 * 第三方支付异步通知服务接口，定义微信/支付宝等渠道回调处理契约。
 *
 * @author CH
 * @since 4.0.0.50
 */
public interface PaymentNotifyService {

    void handleWechatPayNotify(WechatPayNotifyDTO dto);

    void handleWechatRefundNotify(WechatRefundNotifyDTO dto);

    void handleAlipayPayNotify(Long channelId, String orderNo, Map<String, String> params);
}

