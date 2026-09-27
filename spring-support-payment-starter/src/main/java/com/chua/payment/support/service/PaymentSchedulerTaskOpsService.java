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

    /**
     * 列表任务。
     * @return 任务列表
     */
    List<PaymentSchedulerTaskVO> listTasks();

    /**
     * 更新任务。
     * @param taskKey 任务标识
     * @param dto dto
     * @return 更新后的任务
     */
    PaymentSchedulerTaskVO updateTask(String taskKey, PaymentSchedulerTaskUpdateDTO dto);

    /**
     * 手动触发任务。
     * @param taskKey 任务标识
     * @return 触发后的任务
     */
    PaymentSchedulerTaskVO triggerTask(String taskKey);
}
