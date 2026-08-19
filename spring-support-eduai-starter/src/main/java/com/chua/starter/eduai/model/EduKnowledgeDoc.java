package com.chua.starter.eduai.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 知识库文档实体
 *
 * @since 4.0.0.42
 * @author CH
 */
@Data
@TableName("edu_knowledge_doc")
public class EduKnowledgeDoc {

    /**
     * 文档ID
     */
    @TableId(type = IdType.AUTO)
    /** KnowledgeDOCID */
    private Long knowledgeDocId;

    /**
     * 文档名称
     */
    private String knowledgeDocName;

    /**
     * 文档类型
     */
    private String knowledgeDocType;

    /**
     * 文件存储路径
     */
    private String knowledgeDocFilePath;

    /**
     * 文件大小
     */
    private Long knowledgeDocFileSize;

    /**
     * 向量存储ID
     */
    private String knowledgeDocVectorStoreId;

    /**
     * 学科
     */
    private String knowledgeDocSubject;

    /**
     * 学段
     */
    private String knowledgeDocGradeLevel;

    /**
     * 状态
     */
    private Integer knowledgeDocStatus;

    /**
     * 创建时间
     */
    private LocalDateTime knowledgeDocCreateTime;

    /**
     * 更新时间
     */
    private LocalDateTime knowledgeDocUpdateTime;

    /**
     * 创建人
     */
    private String knowledgeDocCreateBy;

    /**
     * 更新人
     */
    private String knowledgeDocUpdateBy;
}