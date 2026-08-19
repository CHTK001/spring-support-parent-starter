package com.chua.starter.eduai.model.vo;

import lombok.Data;

/**
 * 题目VO
 *
 * @since 4.0.0.42
 * @author CH
 */
@Data
public class QuestionVO {

    /** ID */
    private Long id;
    /** 类型 */
    private String type;
    /** Stem */
    private String stem;
    /** Options */
    private String options;
    /** Answer */
    private String answer;
    /** Analysis */
    private String analysis;
    /** 分数 */
    private Integer score;
}