package com.chua.starter.eduai.queue;

import com.chua.starter.eduai.model.dto.MarkingSubmitDTO;

import java.util.List;

/**
 * 阅卷任务载荷。
 *
 * <p>由 {@code EduMarkingServiceImpl} 投递到 {@code edu.marking} 队列，
 * 由 {@link MarkingMessageListener} 异步消费。</p>
 *
 * @param taskId        阅卷任务标识（与 {@code edu_marking_result.marking_result_task_id} 对应）
 * @param answerSheetId 答题卡标识
 * @param items         学生答案列表
 * @since 4.0.0.42
 * @author CH
 */
public record MarkingJob(
        String taskId,
        Long answerSheetId,
        List<MarkingSubmitDTO.AnswerItem> items) {
}
