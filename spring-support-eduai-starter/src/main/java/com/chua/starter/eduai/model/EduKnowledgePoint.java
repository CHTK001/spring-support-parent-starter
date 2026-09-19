package com.chua.starter.eduai.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 知识点实体
 *
 * @since 4.0.0.42
 * @author CH
 */
@Data
@TableName("edu_knowledge_point")
public class EduKnowledgePoint {

    /**
     * 知识点标识
     */
    @TableId(type = IdType.AUTO)
    /** knowledgepointid */
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
     * 知识点描述
     */
    private String knowledgePointDescription;

    /**
     * 向量嵌入JSON
     */
    private String knowledgePointEmbedding;

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
     * 更新时间
     */
    private LocalDateTime knowledgePointUpdateTime;

    /**
     * 创建人
     */
    private String knowledgePointCreateBy;

    /**
     * 更新人
     */
    private String knowledgePointUpdateBy;
}
