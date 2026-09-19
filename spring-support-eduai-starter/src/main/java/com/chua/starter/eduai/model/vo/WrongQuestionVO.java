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

    /**
     * 错题标识
    */
    private Long wrongQuestionId;
    /**
     * 学生标识
    */
    private String wrongQuestionStudentId;
    /**
     * 题目标识
    */
    private Long wrongQuestionQuestionId;
    /**
     * 错误次数
    */
    private Integer wrongQuestionWrongCount;
    /**
     * 掌握度
    */
    private Integer wrongQuestionMasteryLevel;
    /**
     * 最近错误时间
    */
    private LocalDateTime wrongQuestionLastWrongTime;
    /**
     * 最近复习时间
    */
    private LocalDateTime wrongQuestionLastReviewTime;
    /**
     * 复习次数
    */
    private Integer wrongQuestionReviewCount;
}