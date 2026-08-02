package com.chua.starter.eduai.model.dto;

import lombok.Data;

import java.util.List;

/**
 * 教师确认评分DTO
 *
 * @author CH
 * @since 4.0.0.42
 */
@Data
public class MarkingConfirmDTO {

    /**
     * 任务ID
     */
    private String taskId;

    /**
     * 评分调整列表
     */
    private List<ScoreAdjustment> adjustments;

    @Data
    public static class ScoreAdjustment {
        private Long resultId;
        private Integer score;
        private String comment;
    }
}