package com.chua.starter.eduai.model.dto;

import lombok.Data;

import java.util.List;

/**
 * AI组卷请求DTO
 *
 * @author CH
 * @since 4.0.0.42
 */
@Data
public class PaperGenerateDTO {

    /**
     * 试卷名称
     */
    private String name;

    /**
     * 学科
     */
    private String subject;

    /**
     * 学段
     */
    private String gradeLevel;

    /**
     * 年级
     */
    private String grade;

    /**
     * 知识点ID列表
     */
    private List<Long> knowledgePointIds;

    /**
     * 题型及数量配置
     */
    private List<QuestionTypeConfig> typeConfigs;

    /**
     * 难度分布
     */
    private List<Integer> difficultyDistribution;

    /**
     * 总分
     */
    private Integer totalScore;

    /**
     * 考试时长（分钟）
     */
    private Integer duration;

    /**
     * 题目总数
     */
    private Integer questionCount;

    /**
     * 每题分值
     */
    private Integer scorePerQuestion;

    /**
     * 题型配置
     */
    @Data
    public static class QuestionTypeConfig {
        private String type;
        private Integer count;
        private Integer scorePerQuestion;
    }
}