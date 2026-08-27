package com.chua.payment.support.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 钱包日志查询请求参数对象。
 *
 * @param pageNum   页码
 * @param pageSize  每页大小
 * @param merchantId 商户ID
 * @param userId    用户ID
 * @param bizType   业务类型
 * @param bizNo     业务单号
 * @param startTime 开始时间
 * @param endTime   结束时间
 * @author CH
 * @since 4.0.0.50
 */
public record WalletLogQueryRequest(
        int pageNum,
        int pageSize,
        Long merchantId,
        Long userId,
        String bizType,
        String bizNo,
        LocalDateTime startTime,
        LocalDateTime endTime
) {
}
