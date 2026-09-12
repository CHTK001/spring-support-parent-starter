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
    private Long id;

    /**
     * 知识点名称
     */
    private String name;

    /**
     * 知识点编码
     */
    private String code;

    /**
      * 父知识点标识，0表示根节点
     */
    private Long parentId;

    /**
     * 所属学科
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
     * 知识点描述
     */
    private String description;

    /**
     * 排序号
     */
    private Integer sortOrder;

    /**
     * 状态（0-停用 1-启用）
     */
    private Integer status;
}
