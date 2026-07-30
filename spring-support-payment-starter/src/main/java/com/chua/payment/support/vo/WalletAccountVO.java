package com.chua.payment.support.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 钱包账户 VO
 */
@Data
public class WalletAccountVO {
    private Long id;
    private Long merchantId;
    private Long userId;
    private BigDecimal availableBalance;
    private BigDecimal frozenBalance;
    private Integer status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
