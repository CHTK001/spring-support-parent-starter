package com.chua.payment.support.service.impl;

import com.chua.payment.support.dto.PaymentSchedulerTaskUpdateDTO;
import com.chua.payment.support.service.PaymentSchedulerTaskOpsService;
import com.chua.payment.support.service.PaymentTaskSchedulerManager;
import com.chua.payment.support.vo.PaymentSchedulerTaskVO;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 支付内置调度任务运维服务，基于内部线程池管理支付订单超时、通知重试等定时任务。
 *
 * @author CH
 * @since 4.0.0.50
 */
@Service
@RequiredArgsConstructor
@ConditionalOnBean(PaymentTaskSchedulerManager.class)
@ConditionalOnProperty(prefix = "plugin.payment.scheduler", name = "engine", havingValue = "internal", matchIfMissing = true)
public class PaymentInternalSchedulerTaskOpsService implements PaymentSchedulerTaskOpsService {

    /**
     * 支付任务调度器管理器
     */
    private final PaymentTaskSchedulerManager paymentTaskSchedulerManager;

    @Override
    /**
     * 列出全部调度任务
     */
    public List<PaymentSchedulerTaskVO> listTasks() {
        return paymentTaskSchedulerManager.listTasks();
    }

    @Override
    /**
     * 更新任务配置
     */
    public PaymentSchedulerTaskVO updateTask(String taskKey, PaymentSchedulerTaskUpdateDTO dto) {
        return paymentTaskSchedulerManager.updateTask(taskKey, dto);
    }

    @Override
    /**
     * 手动触发任务
     */
    public PaymentSchedulerTaskVO triggerTask(String taskKey) {
        return paymentTaskSchedulerManager.triggerTask(taskKey);
    }
}
