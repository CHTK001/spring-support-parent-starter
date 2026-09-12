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

    /** 标识 */
    private Long id;
    /** 类型 */
    private String type;
    /** Stem */
    private String stem;
    /** 期权 */
    private String options;
    /** Answer */
    private String answer;
    /** Analysis */
    private String analysis;
    /** 分数 */
    private Integer score;
}