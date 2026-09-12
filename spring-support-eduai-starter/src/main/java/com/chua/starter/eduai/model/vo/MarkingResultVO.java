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
* @since 4.0.0.42
* @author CH
 */
@Data
public class MarkingResultVO {

    /** 任务标识 */
    private String taskId;
    /** 状态 */
    private Integer status;
    /** 总数分数 */
    private Integer totalScore;
    /** 总数 */
    private Integer total;
    /** 饰面 */
    private Boolean finished;
    /** 结果 */
    private List<QuestionResult> results;

    @Data
    /**
    * question结果类。
    *
    * @author CH
    * @since 4.0.0
     */
    public static class QuestionResult {
        /** questionid */
        private Long questionId;
        /** 分数 */
        private Integer score;
        /** 评论 */
        private String comment;
        /** Teacher分数 */
        private Integer teacherScore;
        /** Teachercomment */
        private String teacherComment;
    }
}
