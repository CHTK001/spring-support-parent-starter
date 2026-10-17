package com.chua.starter.eduai.model.vo;

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
 * @param taskId          任务标识
 * @param status          状态
 * @param totalScore      总分
 * @param total           总数
 * @param finished        是否批改完成
 * @param results         每题结果
 * @param taskStatus      任务状态：0-待处理 1-处理中 2-已完成 3-失败（异步任务的对外状态）
 * @param stage           当前阶段描述（如「识别中」「判分中 2/3」），供前端展示进度
 * @param error           失败原因（taskStatus=3 时）
 * @param teacherFinished 老师是否已点「阅卷完成」（错题已按最终分归位）
 * @since 4.0.0.42
 * @author CH
 */
public record MarkingResultVO(
        String taskId,
        Integer status,
        Integer totalScore,
        Integer total,
        Boolean finished,
        List<QuestionResult> results,
        Integer taskStatus,
        String stage,
        String error,
        Boolean teacherFinished) {

    /**
     * 单题阅卷结果
     *
     * @param resultId       结果标识（对应 edu_marking_result 主键，教师确认时回传）
     * @param questionId     题目标识
     * @param no             卷面识别题号（任意卷无题库题号，靠它对位；如 "1." "2."）
     * @param studentAnswer  学生作答原文（从识别明细透出，教师对照作答做修正）
     * @param maxScore       该题在试卷上的满分（取自 edu_paper_question 的每题分值），教师修正不得超过此值
     * @param score          分数
     * @param comment        评论
     * @param teacherScore   教师分数
     * @param teacherComment 教师评语
     * @author CH
     * @since 4.0.0
     */
    public record QuestionResult(
            Long resultId,
            Long questionId,
            String no,
            String studentAnswer,
            Integer maxScore,
            Integer score,
            String comment,
            Integer teacherScore,
            String teacherComment) {
    }
}
