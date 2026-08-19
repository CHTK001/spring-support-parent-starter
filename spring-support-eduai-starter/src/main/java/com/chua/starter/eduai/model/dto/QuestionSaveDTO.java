package com.chua.starter.eduai.model.dto;

import lombok.Data;

/**
 * 题目保存DTO
 *
 * @since 4.0.0.42
 * @author CH
 */
@Data
public class QuestionSaveDTO {

    /**
     * 题目ID（更新时必填）
     */
    private Long id;

    /**
     * 题型
     */
    private String type;

    /**
     * 学科
     */
    private String subject;

    /**
     * 年段
     */
    private String gradeLevel;

    /**
     * 年级
     */
    private String grade;

    /**
     * 年份
     */
    private String year;

    /**
     * 难度（1-5）
     */
    private Integer difficulty;

    /**
     * 知识点ID列表（逗号分隔）
     */
    private String knowledgePointIds;

    /**
     * 题干
     */
    private String stem;

    /**
     * 题干图片URL（逗号分隔）
     */
    private String stemImages;

    /**
     * 选项JSON
     */
    private String options;

    /**
     * 标准答案
     */
    private String answer;

    /**
     * 解析
     */
    private String analysis;

    /**
     * 来源
     */
    private String source;

    /**
     * 来源类型（MANUAL/IMPORT/CRAWL）
     */
    private String sourceType;

    /**
     * 审核状态（0-待审核 1-通过 2-驳回）
     */
    private Integer auditStatus;

    /**
     * 状态（0-停用 1-启用）
     */
    private Integer status;
}