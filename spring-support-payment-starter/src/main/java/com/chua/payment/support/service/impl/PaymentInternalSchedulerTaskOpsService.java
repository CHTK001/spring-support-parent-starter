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
 * 支付内置调度任务运维服务。
 */
@Service
@RequiredArgsConstructor
@ConditionalOnBean(PaymentTaskSchedulerManager.class)
@ConditionalOnProperty(prefix = "plugin.payment.scheduler", name = "engine", havingValue = "internal", matchIfMissing = true)
public class PaymentInternalSchedulerTaskOpsService implements PaymentSchedulerTaskOpsService {

    private final PaymentTaskSchedulerManager paymentTaskSchedulerManager;

    @Override
    public List<PaymentSchedulerTaskVO> listTasks() {
        return paymentTaskSchedulerManager.listTasks();
    }

    @Override
    public PaymentSchedulerTaskVO updateTask(String taskKey, PaymentSchedulerTaskUpdateDTO dto) {
        return paymentTaskSchedulerManager.updateTask(taskKey, dto);
    }

    @Override
    public PaymentSchedulerTaskVO triggerTask(String taskKey) {
        return paymentTaskSchedulerManager.triggerTask(taskKey);
    }
}
