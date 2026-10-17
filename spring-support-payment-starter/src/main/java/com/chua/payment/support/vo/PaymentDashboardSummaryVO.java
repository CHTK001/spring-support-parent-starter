package com.chua.payment.support.vo;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 支付首页业务统计汇总
 *
 * @param startTime                开始时间
 * @param endTime                  结束时间
 * @param merchantTotal            商户总数
 * @param activeMerchantCount      活跃商户数量
 * @param configuredMerchantCount  已配置商户数量
 * @param paymentOrderCount        支付订单数量
 * @param refundCount              退款数量
 * @param transactionCount         交易数量
 * @param callbackRequestCount     回调请求数量
 * @param successNotifyCount       成功通知数量
 * @param failedNotifyCount        失败通知数量
 * @param averageProcessDurationMs 平均处理耗时（毫秒）
 * @param totalConsumeAmount       消费总金额
 * @param totalRefundAmount        退款总金额
 * @author CH
 * @since 4.0.0
 */
public record PaymentDashboardSummaryVO(
        LocalDateTime startTime,
        LocalDateTime endTime,
        Long merchantTotal,
        Long activeMerchantCount,
        Long configuredMerchantCount,
        Long paymentOrderCount,
        Long refundCount,
        Long transactionCount,
        Long callbackRequestCount,
        Long successNotifyCount,
        Long failedNotifyCount,
        Long averageProcessDurationMs,
        BigDecimal totalConsumeAmount,
        BigDecimal totalRefundAmount) implements Serializable {

    private static final long serialVersionUID = 1L; // 序列化版本uid
}
