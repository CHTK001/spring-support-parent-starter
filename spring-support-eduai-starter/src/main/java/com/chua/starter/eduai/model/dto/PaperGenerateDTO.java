package com.chua.starter.eduai.model.dto;

import java.util.List;

/**
 * AI组卷请求DTO
 *
 * @param name                   试卷名称
 * @param subject                学科
 * @param gradeLevel             学段
 * @param grade                  年级
 * @param knowledgePointIds      知识点标识列表
 * @param typeConfigs            题型及数量配置
 * @param difficultyDistribution 难度分布
 * @param totalScore             总分
 * @param duration               考试时长（分钟）
 * @param questionCount          题目总数
 * @param scorePerQuestion       每题分值
 * @since 4.0.0.42
 * @author CH
 */
public record PaperGenerateDTO(
        String name,
        String subject,
        String gradeLevel,
        String grade,
        List<Long> knowledgePointIds,
        List<QuestionTypeConfig> typeConfigs,
        List<Integer> difficultyDistribution,
        Integer totalScore,
        Integer duration,
        Integer questionCount,
        Integer scorePerQuestion) {

    /**
     * 题型配置
     *
     * @param type             题型
     * @param count            数量
     * @param scorePerQuestion 每题分值
     * @author CH
     * @since 4.0.0
     */
    public record QuestionTypeConfig(
            String type,
            Integer count,
            Integer scorePerQuestion) {
    }
}
