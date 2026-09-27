package com.chua.payment.support.vo;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 支付首页业务统计汇总
 * @author CH
 * @since 4.0.0
 */
@Data
public class PaymentDashboardSummaryVO implements Serializable {
    private static final long serialVersionUID = 1L; // 序列化版本uid

    /**
     * 开始时间
     */
    private LocalDateTime startTime;

    /**
     * 结束时间
     */
    private LocalDateTime endTime;

    /**
     * 商户总数
     */
    private Long merchantTotal;

    /**
     * 活跃商户数量
     */
    private Long activeMerchantCount;

    /**
     * 已配置商户数量
     */
    private Long configuredMerchantCount;

    /**
     * 支付订单数量
     */
    private Long paymentOrderCount;

    /**
     * 退款数量
     */
    private Long refundCount;

    /**
     * 交易数量
     */
    private Long transactionCount;

    /**
     * 回调请求数量
     */
    private Long callbackRequestCount;

    /**
     * 成功通知数量
     */
    private Long successNotifyCount;

    /**
     * 失败通知数量
     */
    private Long failedNotifyCount;

    /**
     * 平均处理耗时（毫秒）
     */
    private Long averageProcessDurationMs;

    /**
     * 消费总金额
     */
    private BigDecimal totalConsumeAmount;

    /**
     * 退款总金额
     */
    private BigDecimal totalRefundAmount;
}
