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
    * 题目标识（更新时必填）
     */
    private Long questionId;

    /**
    * 题型
     */
    private String questionType;

    /**
    * 学科
     */
    private String questionSubject;

    /**
    * 年段
     */
    private String questionGradeLevel;

    /**
    * 年级
     */
    private String questionGrade;

    /**
    * 年份
     */
    private String questionYear;

    /**
    * 难度（1-5）
     */
    private Integer questionDifficulty;

    /**
    * 知识点标识列表（逗号分隔）
     */
    private String questionKnowledgePointIds;

    /**
    * 题干
     */
    private String questionStem;

    /**
    * 题干图片URL（逗号分隔）
     */
    private String questionStemImages;

    /**
    * 选项JSON
     */
    private String questionOptions;

    /**
    * 标准答案
     */
    private String questionAnswer;

    /**
    * 解析
     */
    private String questionAnalysis;

    /**
    * 来源
     */
    private String questionSource;

    /**
    * 来源类型（MANUAL/导入/CRAWL）
     */
    private String questionSourceType;

    /**
    * 审核状态（0-待审核 1-通过 2-驳回）
     */
    private Integer questionAuditStatus;

    /**
    * 状态（0-停用 1-启用）
     */
    private Integer questionStatus;
}