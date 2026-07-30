package com.chua.starter.tencent.support.payment.dto;

import lombok.Data;

/**
 * 微信支付通知解析结果
 */
@Data
public class TencentWechatPayNotifyPayload {
    private String orderNo;
    private String transactionId;
    private String appId;
    private String merchantId;
    private Long totalAmountFen;
    private Long payerTotalAmountFen;
    private String tradeState;
    private String tradeStateDesc;
}
