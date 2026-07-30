package com.chua.starter.tencent.support.payment.dto;

import lombok.Data;

/**
 * 微信订单查询响应
 */
@Data
public class TencentWechatOrderResponse {
    private boolean success;
    private String transactionId;
    private Long totalAmountFen;
    private Long payerTotalAmountFen;
    private String tradeState;
    private String tradeStateDesc;
    private String rawResponse;
}
