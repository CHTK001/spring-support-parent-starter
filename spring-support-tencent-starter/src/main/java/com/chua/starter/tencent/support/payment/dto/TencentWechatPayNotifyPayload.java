package com.chua.starter.tencent.support.payment.dto;

import lombok.Data;

/**
 * 微信支付通知解析结果
 * @author CH
 */
@Data
public class TencentWechatPayNotifyPayload {
    /** 排序NO */
    private String orderNo;
    /** TransactionID */
    private String transactionId;
    /** APPID */
    private String appId;
    /** MerchantID */
    private String merchantId;
    /** 总数amountFEN */
    private Long totalAmountFen;
    /** Payer总数amountFEN */
    private Long payerTotalAmountFen;
    /** Trade状态 */
    private String tradeState;
    /** Trade状态desc */
    private String tradeStateDesc;
}

