package com.chua.starter.eduai.queue;

import com.chua.starter.eduai.service.IEduMarkingService;
import com.chua.starter.eduai.service.impl.EduMarkingServiceImpl;
import com.chua.starter.queue.Message;
import com.chua.starter.queue.annotation.QueueListener;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 阅卷队列消费者。
 *
 * <p>通过 {@link QueueListener} 订阅 {@link EduMarkingServiceImpl#QUEUE_NAME} 队列，
 * 收到消息后委托 {@link EduMarkingServiceImpl#markSync(String, java.util.List)} 完成批改。</p>
 *
 * <p>消费失败由 {@code queue-starter} 自身的重试/死信策略处理；阅卷任务在数据库侧有 taskId 兜底，
 * 重新提交答题卡即可重试。</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MarkingMessageListener {

    /** Marking服务 */
    private final IEduMarkingService markingService;

    /**
    * 消费阅卷任务。
    *
    * @param job    阅卷任务载荷（由 {@link com.chua.starter.queue.MessageTemplate} 反序列化得到）
    * @param message 原始消息，用于失败日志
    */
    @QueueListener(EduMarkingServiceImpl.QUEUE_NAME)
    public void onMessage(MarkingJob job, Message message) {
        if (job == null || job.getTaskId() == null) {
            log.warn("[EduAi] 阅卷队列收到空消息: id={}", message == null ? null : message.getId());
            return;
        }
        log.info("[EduAi] 阅卷队列消费: taskId={}, items={}",
                job.getTaskId(),
                job.getItems() == null ? 0 : job.getItems().size());
 // edumarking服务impl.mark同步 是 公共，供队列消费调用
        ((EduMarkingServiceImpl) markingService).markSync(job.getTaskId(), job.getItems());
    }
}
