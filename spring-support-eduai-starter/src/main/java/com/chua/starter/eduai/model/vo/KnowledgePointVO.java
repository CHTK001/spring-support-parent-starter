package com.chua.starter.eduai.model.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 知识点VO
 *
 * @since 4.0.0.42
 * @author CH
 */
@Data
public class KnowledgePointVO {

    /**
      * 知识点标识
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
      * 父知识点标识
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
     * 状态
     */
    private Integer status;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 子知识点列表
     */
    private List<KnowledgePointVO> children;
}
