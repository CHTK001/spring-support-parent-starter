package com.chua.starter.eduai.model.vo;

import lombok.Data;

import java.util.List;

/**
 * 阅卷结果 VO。
 *
 * <p>字段：</p>
 * <ul>
 *   <li>{@code taskId} — 任务 ID（提交时返回）</li>
 *   <li>{@code status} — 0 待批改 / 1 AI 已批改 / 2 教师已确认</li>
 *   <li>{@code total} — 题目总数</li>
 *   <li>{@code finished} — 是否批改完成（前端轮询判断结束条件）</li>
 *   <li>{@code totalScore} — 总分</li>
 *   <li>{@code results} — 每题结果（含教师修正）</li>
 * </ul>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Data
public class MarkingResultVO {

    private String taskId;
    private Integer status;
    private Integer totalScore;
    private Integer total;
    private Boolean finished;
    private List<QuestionResult> results;

    @Data
    public static class QuestionResult {
        private Long questionId;
        private Integer score;
        private String comment;
        private Integer teacherScore;
        private String teacherComment;
    }
}
