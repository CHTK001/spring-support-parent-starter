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
     * 父知识点标识
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
     * 状态
     */
    private Integer knowledgePointStatus;

    /**
     * 创建时间
     */
    private LocalDateTime knowledgePointCreateTime;

    /**
     * 子知识点列表
     */
    private List<KnowledgePointVO> children;
}
