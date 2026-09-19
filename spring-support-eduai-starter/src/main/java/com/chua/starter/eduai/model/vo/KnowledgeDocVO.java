package com.chua.starter.eduai.model.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 知识库文档VO
 *
 * @since 4.0.0.42
 * @author CH
 */
@Data
public class KnowledgeDocVO {

    /**
     * 文档标识
     */
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
     * 文件大小（字节）
     */
    private Long knowledgeDocFileSize;

    /**
     * 向量存储标识
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
     * 状态（0-处理中 1-已索引 2-失败）
     */
    private Integer knowledgeDocStatus;

    /**
     * 创建时间
     */
    private LocalDateTime knowledgeDocCreateTime;
}
