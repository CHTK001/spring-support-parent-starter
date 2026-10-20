package com.chua.starter.eduai.model.vo;

import java.time.LocalDateTime;

/**
 * 学生端错题卡（带题目正文，供学生直接复习）。
 *
 * <p>与管理端 {@link WrongQuestionVO} 的区别：管理端只给错题统计行，学生端要能看到
 * 题干、选项与正确答案，因此这里额外联查 {@code edu_question} 的展示字段。</p>
 *
 * <p>这是 JSON 出参载体（可空 DTO），字段允许为空，不做 requireNonNull 加固。</p>
 *
 * @param wrongQuestionId 错题记录标识
 * @param questionId      题目标识
 * @param questionType    题型
 * @param questionSubject 学科
 * @param questionGradeLevel 学段
 * @param questionGrade   年级
 * @param questionDifficulty 难度
 * @param questionStem    题干
 * @param questionOptions 选项（JSON 文本）
 * @param questionAnswer  正确答案
 * @param wrongCount      错误次数
 * @param masteryLevel    掌握度：0 未掌握 / 1 复习中 / 2 已掌握
 * @param reviewCount     复习次数
 * @param lastWrongTime   最近做错时间
 * @param corrected       订正状态：0 未订正 / 1 已订正
 *
 * @author CH
 * @since 4.0.0.43
 */
public record StudentWrongQuestionVO(
        Long wrongQuestionId,
        Long questionId,
        String questionType,
        String questionSubject,
        String questionGradeLevel,
        String questionGrade,
        Integer questionDifficulty,
        String questionStem,
        String questionOptions,
        String questionAnswer,
        Integer wrongCount,
        Integer masteryLevel,
        Integer reviewCount,
        LocalDateTime lastWrongTime,
        Integer corrected) {
}