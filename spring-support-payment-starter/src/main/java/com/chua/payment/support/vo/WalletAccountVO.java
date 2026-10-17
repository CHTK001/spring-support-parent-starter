package com.chua.payment.support.vo;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 钱包账户 VO
 *
 * @param id               标识
 * @param merchantId       商户ID
 * @param userId           用户标识
 * @param availableBalance 可用余额
 * @param frozenBalance    冻结余额
 * @param status           状态
 * @param createdAt        创建时间
 * @param updatedAt        更新时间
 * @author CH
 * @since 4.0.0
 */
public record WalletAccountVO(
        Long id,
        Long merchantId,
        Long userId,
        BigDecimal availableBalance,
        BigDecimal frozenBalance,
        Integer status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {
}
