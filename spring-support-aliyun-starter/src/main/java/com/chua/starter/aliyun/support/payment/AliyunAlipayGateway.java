package com.chua.starter.aliyun.support.payment;

import com.chua.starter.aliyun.support.payment.dto.AliyunAlipayNotifyRequest;
import com.chua.starter.aliyun.support.payment.dto.AliyunAlipayPayNotifyPayload;
import com.chua.starter.aliyun.support.payment.dto.AliyunAlipayPayRequest;
import com.chua.starter.aliyun.support.payment.dto.AliyunAlipayPayResponse;
import com.chua.starter.aliyun.support.payment.dto.AliyunAlipayRefundRequest;
import com.chua.starter.aliyun.support.payment.dto.AliyunAlipayRefundResponse;
import com.chua.starter.aliyun.support.payment.dto.AliyunAlipayTradeQueryResponse;
import com.chua.starter.aliyun.support.payment.dto.AliyunAlipayTransferRequest;
import com.chua.starter.aliyun.support.payment.dto.AliyunAlipayTransferResponse;
import com.chua.starter.aliyun.support.properties.AliyunAlipayProperties;

/**
 * 支付宝网关 SPI
 * @author CH
 * @since 4.0.0
 */
public interface AliyunAlipayGateway {

    /**
     * page薪酬。
     * @param properties 属性
     * @param request 请求
     * @return page薪酬的结果
     */
    AliyunAlipayPayResponse pagePay(AliyunAlipayProperties properties, AliyunAlipayPayRequest request);

    /**
     * wap薪酬。
     * @param properties 属性
     * @param request 请求
     * @return wap薪酬的结果
     */
    AliyunAlipayPayResponse wapPay(AliyunAlipayProperties properties, AliyunAlipayPayRequest request);

    /**
     * 查询订单。
     * @param properties 属性
     * @param orderNo 订单no
     * @return 查询订单的结果
     */
    AliyunAlipayTradeQueryResponse queryOrder(AliyunAlipayProperties properties, String orderNo);

    /**
     * 关闭订单。
     * @param properties 属性
     * @param orderNo 订单no
     * @return 关闭订单的结果
     */
    boolean closeOrder(AliyunAlipayProperties properties, String orderNo);

    /**
     * 退税。
     * @param properties 属性
     * @param request 请求
     * @return 退税的结果
     */
    AliyunAlipayRefundResponse refund(AliyunAlipayProperties properties, AliyunAlipayRefundRequest request);

    /**
     * 查询退税。
     * @param properties 属性
     * @param request 请求
     * @return 查询退税的结果
     */
    AliyunAlipayRefundResponse queryRefund(AliyunAlipayProperties properties, AliyunAlipayRefundRequest request);

    /**
     * 验签并解析支付回调通知。
     *
     * @param properties 支付宝配置
     * @param request    回调通知请求
     * @return 解析后的回调载荷；验签失败返回 {@code null}
     */
    AliyunAlipayPayNotifyPayload verifyAndParsePayNotify(AliyunAlipayProperties properties,
                                                         AliyunAlipayNotifyRequest request);

    /**
     * App 支付。
     * @param properties 支付宝配置
     * @param request    支付请求
     * @return App 支付结果
     */
    AliyunAlipayPayResponse appPay(AliyunAlipayProperties properties, AliyunAlipayPayRequest request);

    /**
     * 单笔转账到支付宝账户
     *
     * @param properties 支付宝配置
     * @param request    转账请求
     * @return 转账结果
     */
    AliyunAlipayTransferResponse transfer(AliyunAlipayProperties properties, AliyunAlipayTransferRequest request);
}

