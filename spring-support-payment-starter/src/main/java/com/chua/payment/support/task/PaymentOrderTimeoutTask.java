package com.chua.payment.support.task;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chua.payment.support.entity.MerchantPaymentConfig;
import com.chua.payment.support.entity.PaymentOrder;
import com.chua.payment.support.mapper.MerchantPaymentConfigMapper;
import com.chua.payment.support.mapper.PaymentOrderMapper;
import com.chua.payment.support.service.PaymentOrderService;
import com.chua.starter.job.support.annotation.Job;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
* 订单超时处理定时任务
* @author CH
* @since 4.0.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "plugin.payment.scheduler", name = "enabled", havingValue = "true", matchIfMissing = true)
public class PaymentOrderTimeoutTask implements PaymentManagedTask {

    /** 排序映射器 */
    private final PaymentOrderMapper orderMapper;
    /** 配置映射器 */
    private final MerchantPaymentConfigMapper configMapper;
    /** Payment排序服务 */
    private final PaymentOrderService paymentOrderService;

    /** cancel超时订单 */
    public void cancelTimeoutOrders() {
        log.info("开始执行订单超时处理任务");

        try {
            List<MerchantPaymentConfig> configs = configMapper.selectList(new LambdaQueryWrapper<MerchantPaymentConfig>()
                    .eq(MerchantPaymentConfig::getAutoCancelTimeoutOrder, true)
                    .isNotNull(MerchantPaymentConfig::getOrderTimeoutMinutes)
                    .gt(MerchantPaymentConfig::getOrderTimeoutMinutes, 0));

            log.info("找到 {} 个商户配置了订单超时自动取消", configs.size());

            int totalCancelled = 0;
            for (MerchantPaymentConfig config : configs) {
                try {
                    int cancelled = cancelMerchantTimeoutOrders(config);
                    totalCancelled += cancelled;
                } catch (Exception e) {
                    log.error("处理商户超时订单失败, merchantId={}", config.getMerchantId(), e);
                }
            }

            log.info("订单超时处理任务执行完成，共取消 {} 个订单", totalCancelled);
        } catch (Exception e) {
            log.error("订单超时处理任务执行失败", e);
        }
    }

    /**
    * cancelmerchant超时订单
    *
    * @param config 配置
    * @return cancelmerchant超时订单的结果
    */
    private int cancelMerchantTimeoutOrders(MerchantPaymentConfig config) {
        LocalDateTime expireTime = LocalDateTime.now().minusMinutes(config.getOrderTimeoutMinutes());

        List<PaymentOrder> timeoutOrders = orderMapper.selectList(new LambdaQueryWrapper<PaymentOrder>()
                .eq(PaymentOrder::getMerchantId, config.getMerchantId())
                .in(PaymentOrder::getStatus, "PENDING", "PAYING")
                .lt(PaymentOrder::getCreatedAt, expireTime)
                .last("limit 100"));

        log.info("商户 {} 找到 {} 个超时订单", config.getMerchantId(), timeoutOrders.size());

        int cancelled = 0;
        for (PaymentOrder order : timeoutOrders) {
            try {
                paymentOrderService.timeoutOrder(order.getId());
                cancelled++;
            } catch (Exception e) {
                log.error("取消超时订单失败, orderId={}, orderNo={}", order.getId(), order.getOrderNo(), e);
            }
        }

        return cancelled;
    }

    @Override
    /** 任务Key */
    public String taskKey() {
        return "payment-order-timeout-scan";
    }

    @Override
    /** 任务名称 */
    public String taskName() {
        return "订单超时扫描";
    }

    @Override
    /** 默认cron */
    public String defaultCron() {
        return "0 */10 * * * ?";
    }

    @Override
    /** Description */
    public String description() {
        return "按商户支付配置扫描超时订单并执行自动取消。";
    }

    @Override
    @Job(
            value = "payment-order-timeout-scan",
            cron = "0 */10 * * * ?",
            desc = "按商户支付配置扫描超时订单并执行自动取消。"
    )
    /** 执行 */
    public void execute() {
        /**
    * cancel超时订单。
    */
        cancelTimeoutOrders();
    }
}

