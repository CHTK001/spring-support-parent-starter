package com.chua.payment.support.channel;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 转账请求
 * @author CH
 * @since 4.0.0
 */
@Data
public class TransferRequest {
    /**
     * 转账单号
     */
    private String transferNo;
    /**
     * 通知回调 URL
     */
    private String notifyUrl;
    /**
     * 商户 ID
     */
    private Long merchantId;
    /**
     * 转出用户标识
     */
    private Long fromUserId;
    /**
     * 转入用户标识
     */
    private Long toUserId;
    /**
     * 转账金额
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
