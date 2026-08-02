package com.chua.starter.eduai.model.vo;

import lombok.Data;

import java.util.List;

/**
 * 阅卷结果VO
 *
 * @author CH
 * @since 4.0.0.42
 */
@Data
public class MarkingResultVO {

    private String taskId;
    private Integer status;
    private Integer totalScore;
    private List<QuestionResult> results;

    @Data
    public static class QuestionResult {
        private Long questionId;
        private Integer score;
        private String comment;
    }
}