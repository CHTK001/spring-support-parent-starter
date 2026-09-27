package com.chua.payment.support.channel;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 提现请求
 * @author CH
 * @since 4.0.0
 */
@Data
public class WithdrawRequest {
    /**
     * 提现单号
     */
    private String withdrawNo;
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
     * 提现金额
     */
    private BigDecimal amount;
    /**
     * 收款银行账号
     */
    private String bankAccount;
    /**
     * 收款银行名称
     */
    private String bankName;
    /**
     * 账户名称
     */
    private String accountName;
    /**
     * 操作人
     */
    private String operator;
    /**
     * 备注
     */
    private String remark;
}
