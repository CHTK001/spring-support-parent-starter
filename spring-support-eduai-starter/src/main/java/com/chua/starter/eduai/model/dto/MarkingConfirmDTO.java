package com.chua.starter.eduai.model.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;

/**
 * 教师确认评分DTO
 *
 * @param taskId       任务标识
 * @param adjustments  评分调整列表
 * @param studentName  手动纠正卷面识别学生（管理员/老师都可提交；空串表示清空该字段）
 * @param studentNo    手动纠正卷面识别学号（管理员/老师都可提交；空串表示清空该字段）
 * @param satisfaction 评语满意度（1=差 2=一般 3=良好 4=优秀 5=极好；null 表示未评价）
 * @since 4.0.0.42
 * @author CH
 */
public record MarkingConfirmDTO(
        String taskId,
        List<ScoreAdjustment> adjustments,
        String studentName,
        String studentNo,
        @Min(1) @Max(5) Integer satisfaction) {

    /**
     * 构造器：保留原有四参数签名，满意度默认为 null。
     *
     * @param taskId       任务标识
     * @param adjustments  评分调整列表
     * @param studentName  手动纠正卷面识别学生
     * @param studentNo    手动纠正卷面识别学号
     */
    public MarkingConfirmDTO(String taskId, List<ScoreAdjustment> adjustments,
                            String studentName, String studentNo) {
        this(taskId, adjustments, studentName, studentNo, null);
    }

    /**
     * 单题评分调整
     *
     * @param resultId 结果标识
     * @param score    分数
     * @param comment  评论
     * @author CH
     * @since 4.0.0
     */
    public record ScoreAdjustment(
            Long resultId,
            Integer score,
            String comment) {
    }
}
