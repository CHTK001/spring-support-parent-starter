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
     * 总分（单题分值）
     */
    private Integer questionScore;

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
     * 审核状态（0-待审核 1-通过 2-驳回；save 接口不采信客户端传值，只能经专用审核接口流转）
     */
    private Integer questionAuditStatus;

    /**
     * 驳回理由（驳回必填；save 接口不采信，只能经专用审核接口写入）
     */
    private String questionAuditRemark;

    /**
     * 保存并审核：管理员新建时直接通过用。非管理员传 true 直接拒绝。
     */
    private Boolean auditOnSave;

    /**
     * 状态（0-停用 1-启用）
     */
    private Integer questionStatus;

    /**
     * 归属用户标识（内部链路传递，如采集任务异步落题时继承任务归属；请求线程以登录态为准，客户端传值无效）
     */
    private String ownerUserId;
}
