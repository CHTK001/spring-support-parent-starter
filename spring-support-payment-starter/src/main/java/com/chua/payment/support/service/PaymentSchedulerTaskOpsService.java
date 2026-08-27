package com.chua.payment.support.service;

import com.chua.payment.support.dto.PaymentSchedulerTaskUpdateDTO;
import com.chua.payment.support.vo.PaymentSchedulerTaskVO;

import java.util.List;

/**
 * 支付调度任务运维服务接口，定义任务列表、更新与手动触发契约。
 *
 * @author CH
 * @since 4.0.0.50
 */
public interface PaymentSchedulerTaskOpsService {

    List<PaymentSchedulerTaskVO> listTasks();

    PaymentSchedulerTaskVO updateTask(String taskKey, PaymentSchedulerTaskUpdateDTO dto);

    PaymentSchedulerTaskVO triggerTask(String taskKey);
}

