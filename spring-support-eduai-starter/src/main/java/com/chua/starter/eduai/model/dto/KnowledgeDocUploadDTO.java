package com.chua.starter.eduai.model.dto;

/**
 * 知识库文档上传DTO
 *
 * @param name 文档名称
 * @param type 文档类型（PDF/WORD/TXT/MARKDOWN）
 * @param filePath 文件存储路径
 * @param fileSize 文件大小（字节）
 * @param subject 学科
 * @param gradeLevel 学段
 *
 * @since 4.0.0.42
 * @author CH
 */
public record KnowledgeDocUploadDTO(
        String name,
        String type,
        String filePath,
        Long fileSize,
        String subject,
        String gradeLevel
) {
}
