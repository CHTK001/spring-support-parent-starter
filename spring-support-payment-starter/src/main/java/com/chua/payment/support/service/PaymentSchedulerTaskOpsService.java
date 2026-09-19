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
     * @return 列表任务的结果
     */
    List<PaymentSchedulerTaskVO> listTasks();

    /**
     * 更新任务。
     * @param taskKey 任务Key
     * @param dto dto
     * @return 更新任务的结果
     */
    PaymentSchedulerTaskVO updateTask(String taskKey, PaymentSchedulerTaskUpdateDTO dto);

    /**
     * trigger任务。
     * @param taskKey 任务Key
     * @return trigger任务的结果
     */
    PaymentSchedulerTaskVO triggerTask(String taskKey);
}

