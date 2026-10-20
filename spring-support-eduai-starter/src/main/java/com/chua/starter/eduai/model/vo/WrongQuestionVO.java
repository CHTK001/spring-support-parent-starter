package com.chua.starter.eduai.model.vo;

import java.time.LocalDateTime;

/**
 * 错题VO
 *
 * @param wrongQuestionId              错题标识
 * @param wrongQuestionStudentId       学生标识（studentNo）
 * @param wrongQuestionQuestionId      题目标识
 * @param wrongQuestionWrongCount      错误次数
 * @param wrongQuestionMasteryLevel    掌握度
 * @param wrongQuestionLastWrongTime   最近错误时间
 * @param wrongQuestionLastReviewTime  最近复习时间
 * @param wrongQuestionReviewCount     复习次数
 * @param wrongQuestionPaperId         试卷标识（错题来自哪张卷，可空）
 * @param wrongQuestionSourceTaskId    来源阅卷任务标识（哪次「完成」登记的，可空）
 * @param wrongQuestionSourceTaskTime  来源阅卷任务的完成时间（溯源展示用，可空）
 * @param wrongQuestionCorrected       订正状态：0 未订正 / 1 已订正
 * @param wrongQuestionCorrectTime     最近订正时间（可空）
 * @param wrongQuestionStudentName     JOIN 补：学生姓名
 * @param wrongQuestionStudentAccount   JOIN 补：学生绑定账号
 * @param wrongQuestionStem            JOIN 补：题目正文
 * @param wrongQuestionSubject         JOIN 补：题目学科
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
        Long wrongQuestionPaperId,
        String wrongQuestionSourceTaskId,
        LocalDateTime wrongQuestionSourceTaskTime,
        Integer wrongQuestionCorrected,
        LocalDateTime wrongQuestionCorrectTime,
        String wrongQuestionStudentName,
        String wrongQuestionStudentAccount,
        String wrongQuestionStem,
        String wrongQuestionSubject) {
}
