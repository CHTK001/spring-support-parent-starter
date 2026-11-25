package com.chua.starter.aliyun.support.payment;

import com.chua.starter.aliyun.support.payment.dto.AliyunAlipayNotifyRequest;
import com.chua.starter.aliyun.support.payment.dto.AliyunAlipayPayNotifyPayload;
import com.chua.starter.aliyun.support.payment.dto.AliyunAlipayPayRequest;
import com.chua.starter.aliyun.support.payment.dto.AliyunAlipayPayResponse;
import com.chua.starter.aliyun.support.payment.dto.AliyunAlipayProfitSharingQueryResponse;
import com.chua.starter.aliyun.support.payment.dto.AliyunAlipayProfitSharingRequest;
import com.chua.starter.aliyun.support.payment.dto.AliyunAlipayProfitSharingResponse;
import com.chua.starter.aliyun.support.payment.dto.AliyunAlipayProfitSharingReturnRequest;
import com.chua.starter.aliyun.support.payment.dto.AliyunAlipayProfitSharingReturnResponse;
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

    /**
     * 创建分账订单。
     *
     * <p>对应支付宝接口 {@code alipay.trade.order.settle}。</p>
     *
     * @param properties 支付宝配置
     * @param request    分账创建请求
     * @return 分账创建结果
     */
    AliyunAlipayProfitSharingResponse createProfitSharingOrder(AliyunAlipayProperties properties, AliyunAlipayProfitSharingRequest request);

    /**
     * 查询分账订单。
     *
     * <p>对应支付宝接口 {@code alipay.trade.order.settle.query}。</p>
     *
     * @param properties   支付宝配置
     * @param outRequestNo 商户分账单号
     * @param tradeNo      支付宝交易号
     * @return 分账查询结果
     */
    AliyunAlipayProfitSharingQueryResponse queryProfitSharingOrder(AliyunAlipayProperties properties, String outRequestNo, String tradeNo);

    /**
     * 分账回退。
     *
     * <p>SDK 未提供独立的分账回退接口模型，按支付宝官方口径走
     * {@code alipay.trade.refund} 携带 {@code refund_royalty_parameters} 实现退分账。</p>
     *
     * @param properties 支付宝配置
     * @param request    分账回退请求
     * @return 分账回退结果
     */
    AliyunAlipayProfitSharingReturnResponse returnProfitSharing(AliyunAlipayProperties properties, AliyunAlipayProfitSharingReturnRequest request);

    /**
     * 查询交易账单下载地址。
     *
     * <p>对应支付宝接口 {@code alipay.data.dataservice.bill.downloadurl.query}，返回的下载地址
     * 有效期有限，调用方应尽快下载。</p>
     *
     * @param properties 支付宝配置
     * @param billDate   账单日期，格式 yyyy-MM-dd
     * @return 账单压缩文件下载地址
     */
    String queryBillDownloadUrl(AliyunAlipayProperties properties, String billDate);
}

