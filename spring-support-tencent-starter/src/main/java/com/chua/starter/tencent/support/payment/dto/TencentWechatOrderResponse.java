package com.chua.starter.tencent.support.payment.dto;

import lombok.Data;

/**
 * 微信订单查询响应
 * @author CH
 */
@Data
public class TencentWechatOrderResponse {
    /** Success */
    private boolean success;
    /** TransactionID */
    private String transactionId;
    /** 总数amountFEN */
    private Long totalAmountFen;
    /** Payer总数amountFEN */
    private Long payerTotalAmountFen;
    /** Trade状态 */
    private String tradeState;
    /** Trade状态desc */
    private String tradeStateDesc;
    /** RAW响应 */
    private String rawResponse;
}

