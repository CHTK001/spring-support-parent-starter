package com.chua.payment.support.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 钱包账户 VO
 * @author CH
 */
@Data
public class WalletAccountVO {
    /** ID */
    private Long id;
    /** MerchantID */
    private Long merchantId;
    /** 用户ID */
    private Long userId;
    /** 可用balance */
    private BigDecimal availableBalance;
    /** Frozenbalance */
    private BigDecimal frozenBalance;
    /** 状态 */
    private Integer status;
    /** 创建时间AT */
    private LocalDateTime createdAt;
    /** 更新时间AT */
    private LocalDateTime updatedAt;
}

