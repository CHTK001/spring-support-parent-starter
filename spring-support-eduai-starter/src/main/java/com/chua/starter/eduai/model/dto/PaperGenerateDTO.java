package com.chua.starter.eduai.model.dto;

import lombok.Data;

import java.util.List;

/**
* AI组卷请求DTO
*
* @since 4.0.0.42
* @author CH
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
    * 知识点标识列表
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
    * @author CH
    * @since 4.0.0
    */
    @Data
    public static class QuestionTypeConfig {
        /** 类型 */
        private String type;
        /** 数量 */
        private Integer count;
        /** 分数perquestion */
        private Integer scorePerQuestion;
    }
}
