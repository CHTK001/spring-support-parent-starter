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
 * 支付首页业务统计服务实现。
 */
@Service
public class PaymentDashboardServiceImpl implements PaymentDashboardService {

    private static final List<String> PAID_ORDER_STATUSES = List.of("PAID", "COMPLETED", "REFUNDING", "REFUNDED");
    private static final int ACTIVE_MERCHANT_STATUS = 1;
    private static final String REFUNDED_STATUS = "REFUNDED";
    private static final String SUCCESS_STATUS = "SUCCESS";

    private final MerchantMapper merchantMapper;
    private final MerchantChannelMapper merchantChannelMapper;
    private final PaymentOrderMapper paymentOrderMapper;
    private final PaymentRefundOrderMapper paymentRefundOrderMapper;
    private final PaymentNotifyLogMapper paymentNotifyLogMapper;
    private final TransactionRecordMapper transactionRecordMapper;

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

        fillMerchantMetrics(summary);
        fillOrderMetrics(summary, startTime, endTime);
        fillRefundMetrics(summary, startTime, endTime);
        fillTransactionMetrics(summary, startTime, endTime);
        fillNotifyMetrics(summary, startTime, endTime);
        return summary;
    }

    private void fillMerchantMetrics(PaymentDashboardSummaryVO summary) {
        summary.setMerchantTotal(merchantMapper.selectCount(new LambdaQueryWrapper<>()));
        summary.setActiveMerchantCount(merchantMapper.selectCount(new LambdaQueryWrapper<Merchant>()
                .eq(Merchant::getStatus, ACTIVE_MERCHANT_STATUS)));

        List<Object> configuredMerchantIds = merchantChannelMapper.selectObjs(new QueryWrapper<MerchantChannel>()
                .select("distinct merchant_id")
                .isNotNull("merchant_id"));
        summary.setConfiguredMerchantCount((long) configuredMerchantIds.size());
    }

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

    private void fillTransactionMetrics(PaymentDashboardSummaryVO summary, LocalDateTime startTime, LocalDateTime endTime) {
        Long transactionCount = transactionRecordMapper.selectCount(new LambdaQueryWrapper<TransactionRecord>()
                .ge(TransactionRecord::getCreatedAt, startTime)
                .le(TransactionRecord::getCreatedAt, endTime));
        summary.setTransactionCount(transactionCount);
    }

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

    private Long processDurationMs(PaymentNotifyLog item) {
        if (item.getReceivedTime() == null || item.getProcessedTime() == null) {
            return null;
        }
        return java.time.Duration.between(item.getReceivedTime(), item.getProcessedTime()).toMillis();
    }

    private BigDecimal firstNonNull(BigDecimal first, BigDecimal second) {
        if (first != null) {
            return first;
        }
        return second != null ? second : BigDecimal.ZERO;
    }
}
