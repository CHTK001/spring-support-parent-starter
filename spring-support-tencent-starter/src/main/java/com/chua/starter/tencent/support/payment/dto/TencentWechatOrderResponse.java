package com.chua.starter.tencent.support.payment.dto;

import lombok.Data;

/**
 * 微信订单查询响应
 * @author CH
 * @since 4.0.0
 */
@Data
public class TencentWechatOrderResponse {
    /**
     * 成功
    */
    private boolean success;
    /**
     * transactionid
    */
    private String transactionId;
    /**
     * 总数amountfen
    */
    private Long totalAmountFen;
    /**
     * 薪酬总数amountfen
    */
    private Long payerTotalAmountFen;
    /**
     * 贸易状态
    */
    private String tradeState;
    /**
     * 贸易状态desc
    */
    private String tradeStateDesc;
    /**
     * RAW响应
    */
    private String rawResponse;
}

