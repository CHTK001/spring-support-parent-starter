package com.chua.starter.eduai.model.dto;

import lombok.Data;

import java.util.List;

/**
 * 教师确认评分DTO
 *
 * @since 4.0.0.42
 * @author CH
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
        /** 结果ID */
        private Long resultId;
        /** 分数 */
        private Integer score;
        /** Comment */
        private String comment;
    }
}