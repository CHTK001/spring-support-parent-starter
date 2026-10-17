package com.chua.starter.tencent.support.payment.dto;

/**
 * 微信支付通知解析结果
 *
 * @param orderNo            排序NO
 * @param transactionId      transactionid
 * @param appId              APPID
 * @param merchantId         merchantid
 * @param totalAmountFen     总数amountfen
 * @param payerTotalAmountFen 薪酬总数amountfen
 * @param tradeState         贸易状态
 * @param tradeStateDesc     贸易状态desc
 * @author CH
 * @since 4.0.0
 */
public record TencentWechatPayNotifyPayload(
        String orderNo,
        String transactionId,
        String appId,
        String merchantId,
        Long totalAmountFen,
        Long payerTotalAmountFen,
        String tradeState,
        String tradeStateDesc) {
}
