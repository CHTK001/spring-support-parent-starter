package com.chua.payment.support.vo;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 支付首页业务统计汇总。
 */
@Data
public class PaymentDashboardSummaryVO implements Serializable {

    private LocalDateTime startTime;

    private LocalDateTime endTime;

    private Long merchantTotal;

    private Long activeMerchantCount;

    private Long configuredMerchantCount;

    private Long paymentOrderCount;

    private Long refundCount;

    private Long transactionCount;

    private Long callbackRequestCount;

    private Long successNotifyCount;

    private Long failedNotifyCount;

    private Long averageProcessDurationMs;

    private BigDecimal totalConsumeAmount;

    private BigDecimal totalRefundAmount;
}
