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

    /**
     * 任务标识
    */
    private String taskId;
    /**
     * 状态
    */
    private Integer status;
    /**
     * 总数分数
    */
    private Integer totalScore;
    /**
     * 总数
    */
    private Integer total;
    /**
     * 饰面
    */
    private Boolean finished;
    /**
     * 结果
    */
    private List<QuestionResult> results;

    /**
     * 任务状态：0-待处理 1-处理中 2-已完成 3-失败（异步任务的对外状态）
     */
    private Integer taskStatus;

    /**
     * 当前阶段描述（如「识别中」「判分中 2/3」），供前端展示进度
     */
    private String stage;

    /**
     * 失败原因（taskStatus=3 时）
     */
    private String error;

    @Data
    /**
     * question结果类。
     *
     * @author CH
     * @since 4.0.0
     */
    public static class QuestionResult {
        /**
         * 结果标识（对应 edu_marking_result 主键，教师确认时回传）
        */
        private Long resultId;
        /**
         * questionid
        */
        private Long questionId;
        /**
         * 卷面识别题号（任意卷无题库题号，靠它对位；如 "1." "2."）
         */
        private String no;
        /**
         * 学生作答原文（从识别明细透出，教师对照作答做修正）
         */
        private String studentAnswer;
        /**
         * 该题在试卷上的满分（取自 edu_paper_question 的每题分值），教师修正不得超过此值
        */
        private Integer maxScore;
        /**
         * 分数
        */
        private Integer score;
        /**
         * 评论
        */
        private String comment;
        /**
         * Teacher分数
        */
        private Integer teacherScore;
        /**
         * Teachercomment
        */
        private String teacherComment;
    }
}
