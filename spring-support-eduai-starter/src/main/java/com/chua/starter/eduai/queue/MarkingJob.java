package com.chua.starter.eduai.queue;

import com.chua.starter.eduai.model.dto.MarkingSubmitDTO;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.util.List;

/**
 * 阅卷任务载荷。
 *
 * <p>由 {@code EduMarkingServiceImpl} 投递到 {@code edu.marking} 队列，
 * 由 {@link MarkingMessageListener} 异步消费。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MarkingJob {

    /**
     * 阅卷任务 ID（与 {@code edu_marking_result.marking_result_task_id} 对应）。
     */
    private String taskId;

    /**
     * 答题卡 ID。
     */
    private Long answerSheetId;

    /**
     * 学生答案列表。
     */
    private List<MarkingSubmitDTO.AnswerItem> items;
}