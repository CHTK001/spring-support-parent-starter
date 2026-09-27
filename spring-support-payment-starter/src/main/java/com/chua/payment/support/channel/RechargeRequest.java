package com.chua.payment.support.channel;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 充值请求
 * @author CH
 * @since 4.0.0
 */
@Data
public class RechargeRequest {
    /**
     * 充值单号
     */
    private String rechargeNo;
    /**
     * 通知回调 URL
     */
    private String notifyUrl;
    /**
     * 商户 ID
     */
    private Long merchantId;
    /**
     * 用户标识
     */
    private Long userId;
    /**
     * 充值金额
     */
    private BigDecimal amount;
    /**
     * 操作人
     */
    private String operator;
    /**
     * 备注
     */
    private String remark;
}
