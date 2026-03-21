package com.chua.starter.tencent.support.payment;

import com.chua.starter.tencent.support.payment.dto.TencentWechatNotifyRequest;
import com.chua.starter.tencent.support.payment.dto.TencentWechatOrderResponse;
import com.chua.starter.tencent.support.payment.dto.TencentWechatPayNotifyPayload;
import com.chua.starter.tencent.support.payment.dto.TencentWechatPayRequest;
import com.chua.starter.tencent.support.payment.dto.TencentWechatPayResponse;
import com.chua.starter.tencent.support.payment.dto.TencentWechatRefundNotifyPayload;
import com.chua.starter.tencent.support.payment.dto.TencentWechatRefundRequest;
import com.chua.starter.tencent.support.payment.dto.TencentWechatRefundResponse;
import com.chua.starter.tencent.support.properties.TencentWechatPayProperties;

/**
 * 微信支付网关 SPI
 */
public interface TencentWechatPayGateway {

    TencentWechatPayResponse jsapiPay(TencentWechatPayProperties properties, TencentWechatPayRequest request);

    TencentWechatPayResponse h5Pay(TencentWechatPayProperties properties, TencentWechatPayRequest request);

    TencentWechatOrderResponse queryJsapiOrder(TencentWechatPayProperties properties, String orderNo);

    TencentWechatOrderResponse queryH5Order(TencentWechatPayProperties properties, String orderNo);

    boolean closeJsapiOrder(TencentWechatPayProperties properties, String orderNo);

    boolean closeH5Order(TencentWechatPayProperties properties, String orderNo);

    TencentWechatRefundResponse refund(TencentWechatPayProperties properties, TencentWechatRefundRequest request);

    TencentWechatRefundResponse queryRefund(TencentWechatPayProperties properties, TencentWechatRefundRequest request);

    TencentWechatPayNotifyPayload parsePayNotify(TencentWechatPayProperties properties, TencentWechatNotifyRequest request);

    TencentWechatRefundNotifyPayload parseRefundNotify(TencentWechatPayProperties properties, TencentWechatNotifyRequest request);

    TencentWechatPayResponse nativePay(TencentWechatPayProperties properties, TencentWechatPayRequest request);

    TencentWechatOrderResponse queryNativeOrder(TencentWechatPayProperties properties, String orderNo);

    boolean closeNativeOrder(TencentWechatPayProperties properties, String orderNo);

    TencentWechatPayResponse appPay(TencentWechatPayProperties properties, TencentWechatPayRequest request);

    TencentWechatOrderResponse queryAppOrder(TencentWechatPayProperties properties, String orderNo);

    boolean closeAppOrder(TencentWechatPayProperties properties, String orderNo);

    TencentWechatPayResponse miniProgramPay(TencentWechatPayProperties properties, TencentWechatPayRequest request);

    TencentWechatOrderResponse queryMiniProgramOrder(TencentWechatPayProperties properties, String orderNo);

    boolean closeMiniProgramOrder(TencentWechatPayProperties properties, String orderNo);
}
