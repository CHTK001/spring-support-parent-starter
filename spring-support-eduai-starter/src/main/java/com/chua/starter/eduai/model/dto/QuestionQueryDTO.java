package com.chua.starter.eduai.model.dto;

import lombok.Data;

/**
 * 题目查询条件DTO
 *
 * @since 4.0.0.42
 * @author CH
 */
@Data
public class QuestionQueryDTO {

    /**
     * 当前页码
     */
    private Integer pageNum = 1;

    /**
     * 每页大小
     */
    private Integer pageSize = 10;

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
     * 题型
     */
    private String type;

    /**
     * 难度（1-5）
     */
    private Integer difficulty;

    /**
     * 知识点标识列表（逗号分隔）
     */
    private String knowledgePointIds;

    /**
     * 审核状态
     */
    private Integer auditStatus;

    /**
     * 状态
     */
    private Integer status;

    /**
     * 排序字段: createTime（默认）/ addCount（被加入试卷次数）/ useCount（使用次数）/ difficulty
     */
    private String orderBy;

    /**
     * 排序方向: asc / desc（默认 desc）
     */
    private String orderDir;
}
