package com.chua.starter.eduai.model.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 错题VO
 *
 * @since 4.0.0.42
 * @author CH
 */
@Data
public class WrongQuestionVO {

    /** ID */
    private Long id;
    /** StudentID */
    private String studentId;
    /** QuestionID */
    private Long questionId;
    /** Wrong数量 */
    private Integer wrongCount;
    /** Mastery级别 */
    private Integer masteryLevel;
    /** 最后wrong时间 */
    private LocalDateTime lastWrongTime;
    /** 最后review时间 */
    private LocalDateTime lastReviewTime;
    /** Review数量 */
    private Integer reviewCount;
}