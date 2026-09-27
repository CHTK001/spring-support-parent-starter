package com.chua.payment.support.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 钱包充值DTO
 * @author CH
 * @since 4.0.0
 */
@Data
public class WalletRechargeDTO {
    /**
     * 商户ID
     */
    private Long merchantId;
    /**
     * 用户标识
     */
    private Long userId;
    /**
     * 充值单号
     */
    private String rechargeNo;
    /**
     * 通知URL
     */
    private String notifyUrl;
    /**
     * 金额
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
