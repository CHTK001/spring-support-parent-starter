package com.chua.payment.support.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
* 钱包账户 VO
* @author CH
* @since 4.0.0
 */
@Data
public class WalletAccountVO {
    /** 标识 */
    private Long id;
    /** merchantid */
    private Long merchantId;
    /** 用户标识 */
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

