package com.chua.starter.eduai.model.vo;

import lombok.Data;

/**
 * 题目VO
 *
 * @author CH
 * @since 4.0.0.42
 */
@Data
public class QuestionVO {

    private Long id;
    private String type;
    private String stem;
    private String options;
    private String answer;
    private String analysis;
    private Integer score;
}