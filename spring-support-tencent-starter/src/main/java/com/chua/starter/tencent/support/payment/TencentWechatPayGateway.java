package com.chua.starter.tencent.support.payment;

import com.chua.starter.tencent.support.payment.dto.TencentWechatNotifyRequest;
import com.chua.starter.tencent.support.payment.dto.TencentWechatOrderResponse;
import com.chua.starter.tencent.support.payment.dto.TencentWechatPayNotifyPayload;
import com.chua.starter.tencent.support.payment.dto.TencentWechatPayRequest;
import com.chua.starter.tencent.support.payment.dto.TencentWechatPayResponse;
import com.chua.starter.tencent.support.payment.dto.TencentWechatPayScoreNotifyPayload;
import com.chua.starter.tencent.support.payment.dto.TencentWechatPayScoreRequest;
import com.chua.starter.tencent.support.payment.dto.TencentWechatPayScoreResponse;
import com.chua.starter.tencent.support.payment.dto.TencentWechatRefundNotifyPayload;
import com.chua.starter.tencent.support.payment.dto.TencentWechatRefundRequest;
import com.chua.starter.tencent.support.payment.dto.TencentWechatRefundResponse;
import com.chua.starter.tencent.support.properties.TencentWechatPayProperties;

/**
 * 微信支付网关 SPI
 * @author CH
 * @since 4.0.0
 */
public interface TencentWechatPayGateway {

    /**
     * jsapi薪酬。
     * @param properties 属性
     * @param request 请求
     * @return jsapi薪酬的结果
     */
    TencentWechatPayResponse jsapiPay(TencentWechatPayProperties properties, TencentWechatPayRequest request);

    /**
     * h5薪酬。
     * @param properties 属性
     * @param request 请求
     * @return h5薪酬的结果
     */
    TencentWechatPayResponse h5Pay(TencentWechatPayProperties properties, TencentWechatPayRequest request);

    /**
     * 查询jsapi订单。
     * @param properties 属性
     * @param orderNo 订单no
     * @return 查询jsapi订单的结果
     */
    TencentWechatOrderResponse queryJsapiOrder(TencentWechatPayProperties properties, String orderNo);

    /**
     * 查询h5订单。
     * @param properties 属性
     * @param orderNo 订单no
     * @return 查询h5订单的结果
     */
    TencentWechatOrderResponse queryH5Order(TencentWechatPayProperties properties, String orderNo);

    /**
     * 关闭jsapi订单。
     * @param properties 属性
     * @param orderNo 订单no
     * @return 关闭jsapi订单的结果
     */
    boolean closeJsapiOrder(TencentWechatPayProperties properties, String orderNo);

    /**
     * 关闭h5订单。
     * @param properties 属性
     * @param orderNo 订单no
     * @return 关闭h5订单的结果
     */
    boolean closeH5Order(TencentWechatPayProperties properties, String orderNo);

    /**
     * 退税。
     * @param properties 属性
     * @param request 请求
     * @return 退税的结果
     */
    TencentWechatRefundResponse refund(TencentWechatPayProperties properties, TencentWechatRefundRequest request);

    /**
     * 查询退税。
     * @param properties 属性
     * @param request 请求
     * @return 查询退税的结果
     */
    TencentWechatRefundResponse queryRefund(TencentWechatPayProperties properties, TencentWechatRefundRequest request);

    /**
     * 解析薪酬通知。
     * @param properties 属性
     * @param request 请求
     * @return 解析薪酬通知的结果
     */
    TencentWechatPayNotifyPayload parsePayNotify(TencentWechatPayProperties properties, TencentWechatNotifyRequest request);

    /**
     * 解析退税通知。
     * @param properties 属性
     * @param request 请求
     * @return 解析退税通知的结果
     */
    TencentWechatRefundNotifyPayload parseRefundNotify(TencentWechatPayProperties properties, TencentWechatNotifyRequest request);

    /**
     * NAT薪酬。
     * @param properties 属性
     * @param request 请求
     * @return NAT薪酬的结果
     */
    TencentWechatPayResponse nativePay(TencentWechatPayProperties properties, TencentWechatPayRequest request);

    /**
     * 查询NAT订单。
     * @param properties 属性
     * @param orderNo 订单no
     * @return 查询NAT订单的结果
     */
    TencentWechatOrderResponse queryNativeOrder(TencentWechatPayProperties properties, String orderNo);

    /**
     * 关闭NAT订单。
     * @param properties 属性
     * @param orderNo 订单no
     * @return 关闭NAT订单的结果
     */
    boolean closeNativeOrder(TencentWechatPayProperties properties, String orderNo);

    /**
     * app薪酬。
     * @param properties 属性
     * @param request 请求
     * @return app薪酬的结果
     */
    TencentWechatPayResponse appPay(TencentWechatPayProperties properties, TencentWechatPayRequest request);

    /**
     * 查询app订单。
     * @param properties 属性
     * @param orderNo 订单no
     * @return 查询app订单的结果
     */
    TencentWechatOrderResponse queryAppOrder(TencentWechatPayProperties properties, String orderNo);

    /**
     * 关闭app订单。
     * @param properties 属性
     * @param orderNo 订单no
     * @return 关闭app订单的结果
     */
    boolean closeAppOrder(TencentWechatPayProperties properties, String orderNo);

    /**
     * miniprogram薪酬。
     * @param properties 属性
     * @param request 请求
     * @return miniprogram薪酬的结果
     */
    TencentWechatPayResponse miniProgramPay(TencentWechatPayProperties properties, TencentWechatPayRequest request);

    /**
     * 查询miniprogram订单。
     * @param properties 属性
     * @param orderNo 订单no
     * @return 查询miniprogram订单的结果
     */
    TencentWechatOrderResponse queryMiniProgramOrder(TencentWechatPayProperties properties, String orderNo);

    /**
     * 关闭miniprogram订单。
     * @param properties 属性
     * @param orderNo 订单no
     * @return 关闭miniprogram订单的结果
     */
    boolean closeMiniProgramOrder(TencentWechatPayProperties properties, String orderNo);

    /**
     * 创建薪酬score订单。
     * @param properties 属性
     * @param request 请求
     * @return 创建薪酬score订单的结果
     */
    TencentWechatPayScoreResponse createPayScoreOrder(TencentWechatPayProperties properties, TencentWechatPayScoreRequest request);

    /**
     * 查询薪酬score订单。
     * @param properties 属性
     * @param request 请求
     * @return 查询薪酬score订单的结果
     */
    TencentWechatPayScoreResponse queryPayScoreOrder(TencentWechatPayProperties properties, TencentWechatPayScoreRequest request);

    /**
     * 完成薪酬score订单。
     * @param properties 属性
     * @param request 请求
     * @return 完成薪酬score订单的结果
     */
    TencentWechatPayScoreResponse completePayScoreOrder(TencentWechatPayProperties properties, TencentWechatPayScoreRequest request);

    /**
     * cancel薪酬score订单。
     * @param properties 属性
     * @param request 请求
     * @return cancel薪酬score订单的结果
     */
    TencentWechatPayScoreResponse cancelPayScoreOrder(TencentWechatPayProperties properties, TencentWechatPayScoreRequest request);

    /**
     * 解析薪酬score通知。
     * @param properties 属性
     * @param request 请求
     * @return 解析薪酬score通知的结果
     */
    TencentWechatPayScoreNotifyPayload parsePayScoreNotify(TencentWechatPayProperties properties, TencentWechatNotifyRequest request);
}

