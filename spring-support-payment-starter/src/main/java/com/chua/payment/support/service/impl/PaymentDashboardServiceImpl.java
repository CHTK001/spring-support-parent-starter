package com.chua.payment.support.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.chua.payment.support.entity.Merchant;
import com.chua.payment.support.entity.MerchantChannel;
import com.chua.payment.support.entity.PaymentNotifyLog;
import com.chua.payment.support.entity.PaymentOrder;
import com.chua.payment.support.entity.PaymentRefundOrder;
import com.chua.payment.support.entity.TransactionRecord;
import com.chua.payment.support.mapper.MerchantChannelMapper;
import com.chua.payment.support.mapper.MerchantMapper;
import com.chua.payment.support.mapper.PaymentNotifyLogMapper;
import com.chua.payment.support.mapper.PaymentOrderMapper;
import com.chua.payment.support.mapper.PaymentRefundOrderMapper;
import com.chua.payment.support.mapper.TransactionRecordMapper;
import com.chua.payment.support.service.PaymentDashboardService;
import com.chua.payment.support.vo.PaymentDashboardSummaryVO;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Objects;

/**
 * 支付首页业务统计服务实现，提供订单、退款、商户等维度的数据汇总。
 *
 * @author CH
 * @since 4.0.0.50
 */
@Service
public class PaymentDashboardServiceImpl implements PaymentDashboardService {

    /** Paid_订单_状态 */
    private static final List<String> PAID_ORDER_STATUSES = List.of("PAID", "COMPLETED", "REFUNDING", "REFUNDED");
    /** 活跃_merchant_状态 */
    private static final int ACTIVE_MERCHANT_STATUS = 1;
    /** 退税_状态 */
    private static final String REFUNDED_STATUS = "REFUNDED";
    /** 成功_状态 */
    private static final String SUCCESS_STATUS = "SUCCESS";

    /** Merchantmapper */
    private final MerchantMapper merchantMapper;
    /** Merchant通道映射器 */
    private final MerchantChannelMapper merchantChannelMapper;
    /** 薪酬排序映射器 */
    private final PaymentOrderMapper paymentOrderMapper;
    /** Paymentrefund排序映射器 */
    private final PaymentRefundOrderMapper paymentRefundOrderMapper;
    /** Paymentnotify日志映射器 */
    private final PaymentNotifyLogMapper paymentNotifyLogMapper;
    /** Transactionrecordmapper */
    private final TransactionRecordMapper transactionRecordMapper;

    /**
      * 创建 薪酬dashboard服务impl 实例
     * @param merchantMapper merchant映射器
     * @param merchantChannelMapper merchant通道映射器
     * @param paymentOrderMapper 薪酬订单映射器
     * @param paymentRefundOrderMapper 薪酬退税订单映射器
     * @param paymentNotifyLogMapper 薪酬通知日志映射器
     * @param transactionRecordMapper transactionrecord映射器
     */
    public PaymentDashboardServiceImpl(MerchantMapper merchantMapper,
                                       MerchantChannelMapper merchantChannelMapper,
                                       PaymentOrderMapper paymentOrderMapper,
                                       PaymentRefundOrderMapper paymentRefundOrderMapper,
                                       PaymentNotifyLogMapper paymentNotifyLogMapper,
                                       TransactionRecordMapper transactionRecordMapper) {
        this.merchantMapper = merchantMapper;
        this.merchantChannelMapper = merchantChannelMapper;
        this.paymentOrderMapper = paymentOrderMapper;
        this.paymentRefundOrderMapper = paymentRefundOrderMapper;
        this.paymentNotifyLogMapper = paymentNotifyLogMapper;
        this.transactionRecordMapper = transactionRecordMapper;
    }

    @Override
    /** Summary */
    public PaymentDashboardSummaryVO summary(LocalDate startDate, LocalDate endDate) {
        LocalDate normalizedEndDate = endDate != null ? endDate : LocalDate.now();
        LocalDate normalizedStartDate = startDate != null ? startDate : normalizedEndDate;
        if (normalizedStartDate.isAfter(normalizedEndDate)) {
            LocalDate temp = normalizedStartDate;
            normalizedStartDate = normalizedEndDate;
            normalizedEndDate = temp;
        }

        LocalDateTime startTime = normalizedStartDate.atStartOfDay();
        LocalDateTime endTime = normalizedEndDate.atTime(LocalTime.MAX);

        PaymentDashboardSummaryVO summary = new PaymentDashboardSummaryVO();
        summary.setStartTime(startTime);
        summary.setEndTime(endTime);

        /**
         * fillmerchant指标。
         * @param summary summary
         */
        fillMerchantMetrics(summary);
        /**
         * fill订单指标。
         * @param summary summary
         * @param startTime 启动时间
         * @param endTime 结束时间
         */
        fillOrderMetrics(summary, startTime, endTime);
        /**
         * fill退税指标。
         * @param summary summary
         * @param startTime 启动时间
         * @param endTime 结束时间
         */
        fillRefundMetrics(summary, startTime, endTime);
        /**
         * filltransaction指标。
         * @param summary summary
         * @param startTime 启动时间
         * @param endTime 结束时间
         */
        fillTransactionMetrics(summary, startTime, endTime);
        /**
         * fill通知指标。
         * @param summary summary
         * @param startTime 启动时间
         * @param endTime 结束时间
         */
        fillNotifyMetrics(summary, startTime, endTime);
        return summary;
    }

    /**
     * fillmerchant指标
     *
     * @param summary summary
     */
    private void fillMerchantMetrics(PaymentDashboardSummaryVO summary) {
        summary.setMerchantTotal(merchantMapper.selectCount(new LambdaQueryWrapper<>()));
        summary.setActiveMerchantCount(merchantMapper.selectCount(new LambdaQueryWrapper<Merchant>()
                .eq(Merchant::getStatus, ACTIVE_MERCHANT_STATUS)));

        List<Object> configuredMerchantIds = merchantChannelMapper.selectObjs(new QueryWrapper<MerchantChannel>()
                .select("distinct pay_merchant_id")
                .isNotNull("pay_merchant_id"));
        summary.setConfiguredMerchantCount((long) configuredMerchantIds.size());
    }

    /**
     * fill订单指标
     *
     * @param summary summary
     * @param startTime 启动时间
     * @param endTime 结束时间
     */
    private void fillOrderMetrics(PaymentDashboardSummaryVO summary, LocalDateTime startTime, LocalDateTime endTime) {
        List<PaymentOrder> orders = paymentOrderMapper.selectList(new LambdaQueryWrapper<PaymentOrder>()
                .in(PaymentOrder::getStatus, PAID_ORDER_STATUSES)
                .ge(PaymentOrder::getPayTime, startTime)
                .le(PaymentOrder::getPayTime, endTime));

        summary.setPaymentOrderCount((long) orders.size());
        summary.setTotalConsumeAmount(orders.stream()
                .map(item -> firstNonNull(item.getPaidAmount(), item.getOrderAmount()))
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add));
    }

    /**
     * fill退税指标
     *
     * @param summary summary
     * @param startTime 启动时间
     * @param endTime 结束时间
     */
    private void fillRefundMetrics(PaymentDashboardSummaryVO summary, LocalDateTime startTime, LocalDateTime endTime) {
        List<PaymentRefundOrder> refunds = paymentRefundOrderMapper.selectList(new LambdaQueryWrapper<PaymentRefundOrder>()
                .eq(PaymentRefundOrder::getStatus, REFUNDED_STATUS)
                .ge(PaymentRefundOrder::getUpdatedAt, startTime)
                .le(PaymentRefundOrder::getUpdatedAt, endTime));

        summary.setRefundCount((long) refunds.size());
        summary.setTotalRefundAmount(refunds.stream()
                .map(PaymentRefundOrder::getRefundAmount)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add));
    }

    /**
     * filltransaction指标
     *
     * @param summary summary
     * @param startTime 启动时间
     * @param endTime 结束时间
     */
    private void fillTransactionMetrics(PaymentDashboardSummaryVO summary, LocalDateTime startTime, LocalDateTime endTime) {
        Long transactionCount = transactionRecordMapper.selectCount(new LambdaQueryWrapper<TransactionRecord>()
                .ge(TransactionRecord::getCreatedAt, startTime)
                .le(TransactionRecord::getCreatedAt, endTime));
        summary.setTransactionCount(transactionCount);
    }

    /**
     * Fill通知指标
     *
     * @param summary summary
     * @param startTime 启动时间
     * @param endTime 结束时间
     */
    private void fillNotifyMetrics(PaymentDashboardSummaryVO summary, LocalDateTime startTime, LocalDateTime endTime) {
        List<PaymentNotifyLog> logs = paymentNotifyLogMapper.selectList(new LambdaQueryWrapper<PaymentNotifyLog>()
                .ge(PaymentNotifyLog::getReceivedTime, startTime)
                .le(PaymentNotifyLog::getReceivedTime, endTime));

        long successCount = logs.stream()
                .filter(item -> SUCCESS_STATUS.equalsIgnoreCase(item.getProcessStatus()))
                .count();
        long totalCount = logs.size();
        long averageDurationMs = Math.round(logs.stream()
                .map(this::processDurationMs)
                .filter(Objects::nonNull)
                .mapToLong(Long::longValue)
                .average()
                .orElse(0));

        summary.setCallbackRequestCount(totalCount);
        summary.setSuccessNotifyCount(successCount);
        summary.setFailedNotifyCount(Math.max(totalCount - successCount, 0));
        summary.setAverageProcessDurationMs(averageDurationMs);
    }

    /**
     * 处理持续时间ms
     *
     * @param item item
     * @return 处理持续时间ms的结果
     */
    private Long processDurationMs(PaymentNotifyLog item) {
        if (item.getReceivedTime() == null || item.getProcessedTime() == null) {
            return null;
        }
        return java.time.Duration.between(item.getReceivedTime(), item.getProcessedTime()).toMillis();
    }

    /**
     * 第一个non空
     *
     * @param first 第一个
     * @param second second
     * @return 第一个non空的结果
     */
    private BigDecimal firstNonNull(BigDecimal first, BigDecimal second) {
        if (first != null) {
            return first;
        }
        return second != null ? second : BigDecimal.ZERO;
    }
}

