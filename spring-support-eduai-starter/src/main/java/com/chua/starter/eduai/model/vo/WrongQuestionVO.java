package com.chua.starter.eduai.model.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 错题VO
 *
 * @author CH
 * @since 4.0.0.42
 */
@Data
public class WrongQuestionVO {

    private Long id;
    private String studentId;
    private Long questionId;
    private Integer wrongCount;
    private Integer masteryLevel;
    private LocalDateTime lastWrongTime;
    private LocalDateTime lastReviewTime;
    private Integer reviewCount;
}