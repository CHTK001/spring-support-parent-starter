package com.chua.starter.eduai.model.vo;

import java.time.LocalDateTime;

/**
 * 知识库文档VO
 *
 * @param knowledgeDocId 文档标识
 * @param knowledgeDocName 文档名称
 * @param knowledgeDocType 文档类型
 * @param knowledgeDocFilePath 文件存储路径
 * @param knowledgeDocFileSize 文件大小（字节）
 * @param knowledgeDocVectorStoreId 向量存储标识
 * @param knowledgeDocSubject 学科
 * @param knowledgeDocGradeLevel 学段
 * @param knowledgeDocStatus 状态（0-处理中 1-已索引 2-失败）
 * @param createTime 创建时间
 *
 * @since 4.0.0.42
 * @author CH
 */
public record KnowledgeDocVO(
        Long knowledgeDocId,
        String knowledgeDocName,
        String knowledgeDocType,
        String knowledgeDocFilePath,
        Long knowledgeDocFileSize,
        String knowledgeDocVectorStoreId,
        String knowledgeDocSubject,
        String knowledgeDocGradeLevel,
        Integer knowledgeDocStatus,
        LocalDateTime createTime
) {
}
