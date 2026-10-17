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
 * 支付首页业务统计服务实现，提供订单、交易、商户等维度的数据汇总。
 *
 * @author CH
 * @since 4.0.0.50
 */
@Service
public class PaymentDashboardServiceImpl implements PaymentDashboardService {

    /**
     * 已支付订单状态集合（计入消费金额）
     */
    private static final List<String> PAID_ORDER_STATUSES = List.of("PAID", "COMPLETED", "REFUNDING", "REFUNDED");
    /**
     * 活跃商户状态
     */
    private static final int ACTIVE_MERCHANT_STATUS = 1;
    /**
     * 退款完成状态
     */
    private static final String REFUNDED_STATUS = "REFUNDED";
    /**
     * 回调处理成功状态
     */
    private static final String SUCCESS_STATUS = "SUCCESS";

    /**
     * 商户映射器
     */
    private final MerchantMapper merchantMapper;
    /**
     * 商户渠道映射器
     */
    private final MerchantChannelMapper merchantChannelMapper;
    /**
     * 支付订单映射器
     */
    private final PaymentOrderMapper paymentOrderMapper;
    /**
     * 支付退款单映射器
     */
    private final PaymentRefundOrderMapper paymentRefundOrderMapper;
    /**
     * 支付通知日志映射器
     */
    private final PaymentNotifyLogMapper paymentNotifyLogMapper;
    /**
     * 交易流水映射器
     */
    private final TransactionRecordMapper transactionRecordMapper;

    /**
     * 构造支付首页业务统计服务。
     * @param merchantMapper 商户映射器
     * @param merchantChannelMapper 商户渠道映射器
     * @param paymentOrderMapper 支付订单映射器
     * @param paymentRefundOrderMapper 支付退款单映射器
     * @param paymentNotifyLogMapper 支付通知日志映射器
     * @param transactionRecordMapper 交易流水映射器
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
    /**
     * 查询支付首页汇总数据
     */
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

        // 商户维度指标
        Long merchantTotal = merchantMapper.selectCount(new LambdaQueryWrapper<>());
        Long activeMerchantCount = merchantMapper.selectCount(new LambdaQueryWrapper<Merchant>()
                .eq(Merchant::getStatus, ACTIVE_MERCHANT_STATUS));
        List<Object> configuredMerchantIds = merchantChannelMapper.selectObjs(new QueryWrapper<MerchantChannel>()
                .select("distinct pay_merchant_id")
                .isNotNull("pay_merchant_id"));
        Long configuredMerchantCount = (long) configuredMerchantIds.size();

        // 订单维度指标
        List<PaymentOrder> orders = paymentOrderMapper.selectList(new LambdaQueryWrapper<PaymentOrder>()
                .in(PaymentOrder::getStatus, PAID_ORDER_STATUSES)
                .ge(PaymentOrder::getPayTime, startTime)
                .le(PaymentOrder::getPayTime, endTime));
        Long paymentOrderCount = (long) orders.size();
        BigDecimal totalConsumeAmount = orders.stream()
                .map(item -> firstNonNull(item.getPaidAmount(), item.getOrderAmount()))
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // 退款维度指标
        List<PaymentRefundOrder> refunds = paymentRefundOrderMapper.selectList(new LambdaQueryWrapper<PaymentRefundOrder>()
                .eq(PaymentRefundOrder::getStatus, REFUNDED_STATUS)
                .ge(PaymentRefundOrder::getUpdatedAt, startTime)
                .le(PaymentRefundOrder::getUpdatedAt, endTime));
        Long refundCount = (long) refunds.size();
        BigDecimal totalRefundAmount = refunds.stream()
                .map(PaymentRefundOrder::getRefundAmount)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // 流水维度指标
        Long transactionCount = transactionRecordMapper.selectCount(new LambdaQueryWrapper<TransactionRecord>()
                .ge(TransactionRecord::getCreatedAt, startTime)
                .le(TransactionRecord::getCreatedAt, endTime));

        // 回调通知维度指标
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

        return new PaymentDashboardSummaryVO(
                startTime,
                endTime,
                merchantTotal,
                activeMerchantCount,
                configuredMerchantCount,
                paymentOrderCount,
                refundCount,
                transactionCount,
                totalCount,
                successCount,
                Math.max(totalCount - successCount, 0),
                averageDurationMs,
                totalConsumeAmount,
                totalRefundAmount);
    }

    /**
     * 计算回调处理耗时（毫秒）
     *
     * @param item 回调通知日志
     * @return 处理耗时毫秒数，时间不完整时返回 null
     */
    private Long processDurationMs(PaymentNotifyLog item) {
        if (item.getReceivedTime() == null || item.getProcessedTime() == null) {
            return null;
        }
        return java.time.Duration.between(item.getReceivedTime(), item.getProcessedTime()).toMillis();
    }

    /**
     * 返回第一个非空金额
     *
     * @param first 第一个值
     * @param second 第二个值
     * @return 第一个非空值，均为空时返回零
     */
    private BigDecimal firstNonNull(BigDecimal first, BigDecimal second) {
        if (first != null) {
            return first;
        }
        return second != null ? second : BigDecimal.ZERO;
    }
}
