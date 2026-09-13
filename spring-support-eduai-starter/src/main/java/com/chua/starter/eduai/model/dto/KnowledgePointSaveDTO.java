package com.chua.starter.eduai.model.dto;

import lombok.Data;

/**
* 知识点保存DTO
*
* @since 4.0.0.42
* @author CH
 */
@Data
public class KnowledgePointSaveDTO {

    /**
    * 知识点标识（更新时必填）
     */
    private Long knowledgePointId;

    /**
    * 知识点名称
     */
    private String knowledgePointName;

    /**
    * 知识点编码
     */
    private String knowledgePointCode;

    /**
    * 父知识点标识，0表示根节点
     */
    private Long knowledgePointParentId;

    /**
    * 所属学科
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
    * 知识点描述
     */
    private String knowledgePointDescription;

    /**
    * 排序号
     */
    private Integer knowledgePointSortOrder;

    /**
    * 状态（0-停用 1-启用）
     */
    private Integer knowledgePointStatus;
}