package com.chua.starter.eduai.model.dto;

import java.util.List;

/**
 * 提交答题卡DTO
 *
 * @param examId    考试标识
 * @param studentId 学生标识
 * @param content   答题内容JSON
 * @param answers   答题列表
 * @since 4.0.0.42
 * @author CH
 */
public record MarkingSubmitDTO(
        Long examId,
        String studentId,
        String content,
        List<AnswerItem> answers) {

    /**
     * 单题作答
     *
     * @param questionId 题目标识
     * @param answer     作答内容
     * @author CH
     * @since 4.0.0
     */
    public record AnswerItem(
            Long questionId,
            String answer) {
    }
}
