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

    /**
     * 标识
    */
    private Long id;
    /**
     * 类型
    */
    private String type;
    /**
     * Stem
    */
    private String stem;
    /**
     * 期权
    */
    private String options;
    /**
     * Answer
    */
    private String answer;
    /**
     * Analysis
    */
    private String analysis;
    /**
     * 分数
     */
    private Integer score;
    /**
     * 试卷题唯一编号（手动组卷题项，二维码内容）
     */
    private String questionNo;
    /**
     * 试卷内顺序（手动组卷题项）
     */
    private Integer order;
    /**
     * 题组/大题标识（手动组卷题项）
     */
    private String section;
}