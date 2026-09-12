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

    /** 标识 */
    private Long id;
    /** studentid */
    private String studentId;
    /** questionid */
    private Long questionId;
    /** Wrong数量 */
    private Integer wrongCount;
    /** Mastery级别 */
    private Integer masteryLevel;
    /** 最后wrong时间 */
    private LocalDateTime lastWrongTime;
    /** 最后评审时间 */
    private LocalDateTime lastReviewTime;
    /** 评审数量 */
    private Integer reviewCount;
}