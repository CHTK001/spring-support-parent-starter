package com.chua.starter.eduai.model.vo;

import java.time.LocalDateTime;

/**
 * 错题VO
 *
 * @param wrongQuestionId              错题标识
 * @param wrongQuestionStudentId       学生标识
 * @param wrongQuestionQuestionId      题目标识
 * @param wrongQuestionWrongCount      错误次数
 * @param wrongQuestionMasteryLevel    掌握度
 * @param wrongQuestionLastWrongTime   最近错误时间
 * @param wrongQuestionLastReviewTime  最近复习时间
 * @param wrongQuestionReviewCount     复习次数
 * @param wrongQuestionPaperId         试卷标识（错题来自哪张卷，可空）
 * @since 4.0.0.42
 * @author CH
 */
public record WrongQuestionVO(
        Long wrongQuestionId,
        String wrongQuestionStudentId,
        Long wrongQuestionQuestionId,
        Integer wrongQuestionWrongCount,
        Integer wrongQuestionMasteryLevel,
        LocalDateTime wrongQuestionLastWrongTime,
        LocalDateTime wrongQuestionLastReviewTime,
        Integer wrongQuestionReviewCount,
        Long wrongQuestionPaperId) {
}
