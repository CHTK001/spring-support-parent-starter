package com.chua.payment.support.vo;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
* 支付首页业务统计汇总。
* @author CH
* @since 4.0.0
 */
@Data
public class PaymentDashboardSummaryVO implements Serializable {
    private static final long serialVersionUID = 1L; // 串行版本uid

    /** 开始时间 */
    private LocalDateTime startTime;

    /** 结束时间 */
    private LocalDateTime endTime;

    /** Merchant总数 */
    private Long merchantTotal;

    /** 是否激活merchant数量 */
    private Long activeMerchantCount;

    /** Configuredmerchant数量 */
    private Long configuredMerchantCount;

    /** Payment排序数量 */
    private Long paymentOrderCount;

    /** 退税数量 */
    private Long refundCount;

    /** Transaction数量 */
    private Long transactionCount;

    /** Callback请求数量 */
    private Long callbackRequestCount;

    /** Successnotify数量 */
    private Long successNotifyCount;

    /** Failednotify数量 */
    private Long failedNotifyCount;

    /** Averageprocess持续时间MS */
    private Long averageProcessDurationMs;

    /** 总数consumeamount */
    private BigDecimal totalConsumeAmount;

    /** 总数refundamount */
    private BigDecimal totalRefundAmount;
}
