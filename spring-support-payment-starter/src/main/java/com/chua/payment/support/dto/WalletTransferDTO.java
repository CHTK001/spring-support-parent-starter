package com.chua.payment.support.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 钱包转账DTO
 * @author CH
 * @since 4.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WalletTransferDTO {
    /**
     * 商户ID
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
     * 调拨单号
     */
    private String transferNo;
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
