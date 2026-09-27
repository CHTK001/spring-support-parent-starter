package com.chua.starter.eduai.model.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 知识卡片VO（扁平列表，带文本/附件计数）
 *
 * @since 4.0.0.42
 * @author CH
 */
@Data
public class KnowledgeCardVO {

    /**
     * 知识点（卡片）标识
     */
    private Long knowledgePointId;

    /**
     * 卡片名称
     */
    private String knowledgePointName;

    /**
     * 知识点编码
     */
    private String knowledgePointCode;

    /**
     * 父知识点标识
     */
    private Long knowledgePointParentId;

    /**
     * 学科
     */
    private String knowledgePointSubject;

    /**
     * 学段
     */
    private String knowledgePointGradeLevel;

    /**
     * 年级
     */
    private String knowledgePointGrade;

    /**
     * 描述
     */
    private String knowledgePointDescription;

    /**
     * 排序号
     */
    private Integer knowledgePointSortOrder;

    /**
     * 状态: 0-停用 1-启用
     */
    private Integer knowledgePointStatus;

    /**
     * 是否已有正文（富文本）
     */
    private Boolean hasContent;

    /**
     * 有效附件条数
     */
    private Long fileCount;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;
}
