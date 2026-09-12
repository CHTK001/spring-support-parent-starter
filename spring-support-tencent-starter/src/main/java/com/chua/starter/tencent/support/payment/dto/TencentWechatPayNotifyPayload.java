package com.chua.starter.tencent.support.payment.dto;

import lombok.Data;

/**
* 微信支付通知解析结果
* @author CH
* @since 4.0.0
 */
@Data
public class TencentWechatPayNotifyPayload {
    /** 排序NO */
    private String orderNo;
    /** transactionid */
    private String transactionId;
    /** APPID */
    private String appId;
    /** merchantid */
    private String merchantId;
    /** 总数amountfen */
    private Long totalAmountFen;
    /** 薪酬总数amountfen */
    private Long payerTotalAmountFen;
    /** 贸易状态 */
    private String tradeState;
    /** 贸易状态desc */
    private String tradeStateDesc;
}

