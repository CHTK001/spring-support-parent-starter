package com.chua.payment.support.service;

import com.chua.payment.support.dto.PaymentSchedulerTaskUpdateDTO;
import com.chua.payment.support.vo.PaymentSchedulerTaskVO;

import java.util.List;

/**
 * 支付调度任务运维服务。
 */
public interface PaymentSchedulerTaskOpsService {

    List<PaymentSchedulerTaskVO> listTasks();

    PaymentSchedulerTaskVO updateTask(String taskKey, PaymentSchedulerTaskUpdateDTO dto);

    PaymentSchedulerTaskVO triggerTask(String taskKey);
}
