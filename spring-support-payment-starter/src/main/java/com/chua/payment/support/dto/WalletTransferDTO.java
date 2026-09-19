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
     * merchantid
    */
    private Long merchantId;
    /**
     * 从用户标识
    */
    private Long fromUserId;
    /**
     * 转为用户标识
    */
    private Long toUserId;
    /**
     * 调动no
    */
    private String transferNo;
    /**
     * 通知url
    */
    private String notifyUrl;
    /**
     * Amount
    */
    private BigDecimal amount;
    /**
     * 操作符
    */
    private String operator;
    /**
     * 评论
    */
    private String remark;
}

