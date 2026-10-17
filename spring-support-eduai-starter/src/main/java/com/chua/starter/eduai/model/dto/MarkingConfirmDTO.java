package com.chua.starter.eduai.model.dto;

import java.util.List;

/**
 * 教师确认评分DTO
 *
 * @param taskId       任务标识
 * @param adjustments  评分调整列表
 * @param studentName  手动纠正卷面识别学生（管理员/老师都可提交；空串表示清空该字段）
 * @param studentNo    手动纠正卷面识别学号（管理员/老师都可提交；空串表示清空该字段）
 * @since 4.0.0.42
 * @author CH
 */
public record MarkingConfirmDTO(
        String taskId,
        List<ScoreAdjustment> adjustments,
        String studentName,
        String studentNo) {

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
