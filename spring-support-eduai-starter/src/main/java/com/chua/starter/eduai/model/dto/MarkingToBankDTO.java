package com.chua.starter.eduai.model.dto;

import java.util.List;
import java.util.Map;

/**
 * 外来卷题目加入题库的请求。
 *
 * <p>只在 {@code mode=ANY}（任意卷）下允许：自有卷的题目本来就来自题库，没有「再入库」的意义，
 * 服务端按任务卷型拒绝，前端也不给入口。</p>
 *
 * <p>题干、学生作答一律由服务端回读阅卷结果明细（{@code edu_marking_result.marking_result_detail}），
 * 客户端只提交「选哪几题」与「老师补写的标准答案」，防止前端篡改题干造成题库污染。</p>
 *
 * @param resultIds            选中的阅卷结果行标识（必填，去重后逐条处理）
 * @param questionSubject      学科（必填，整批共用）
 * @param questionGradeLevel   学段：小学/初中/高中/大学（必填）
 * @param questionGrade        年级（必填，如「五年级」）
 * @param questionDifficulty   难度 1-5，缺省 3
 * @param questionScore        单题分值，缺省取该题本次满分
 * @param answers              老师补写的标准答案：resultId → 答案。
 *                             未提供的题按「本次 AI 判为满分」取学生作答作为标准答案；
 *                             判错且未补写的题不入库，随结果返回给老师补齐。
 * @since 4.0.0.42
 * @author CH
 */
public record MarkingToBankDTO(
        List<Long> resultIds,
        String questionSubject,
        String questionGradeLevel,
        String questionGrade,
        Integer questionDifficulty,
        Integer questionScore,
        Map<Long, String> answers) {
}
