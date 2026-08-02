package com.chua.starter.eduai.model.dto;

import lombok.Data;

import java.util.List;

/**
 * 提交答题卡DTO
 *
 * @author CH
 * @since 4.0.0.42
 */
@Data
public class MarkingSubmitDTO {

    /**
     * 考试ID
     */
    private Long examId;

    /**
     * 学生ID
     */
    private String studentId;

    /**
     * 答题内容JSON
     */
    private String content;

    /**
     * 答题列表
     */
    private List<AnswerItem> answers;

    @Data
    public static class AnswerItem {
        private Long questionId;
        private String answer;
    }
}