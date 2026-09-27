package com.chua.starter.tencent.support.payment.dto;

/**
 * 微信订单查询响应
 *
 * @param success 成功
 * @param transactionId transactionid
 * @param totalAmountFen 总数amountfen
 * @param payerTotalAmountFen 薪酬总数amountfen
 * @param tradeState 贸易状态
 * @param tradeStateDesc 贸易状态desc
 * @param rawResponse RAW响应
 *
 * @author CH
 * @since 4.0.0
 */
public record TencentWechatOrderResponse(
        boolean success,
        String transactionId,
        Long totalAmountFen,
        Long payerTotalAmountFen,
        String tradeState,
        String tradeStateDesc,
        String rawResponse
) {
}

