package com.chua.starter.eduai.model.dto;

import lombok.Data;

/**
 * 知识库文档上传DTO
 *
 * @since 4.0.0.42
 * @author CH
 */
@Data
public class KnowledgeDocUploadDTO {

    /**
     * 文档名称
     */
    private String name;

    /**
     * 文档类型（PDF/WORD/TXT/MARKDOWN）
     */
    private String type;

    /**
     * 文件存储路径
     */
    private String filePath;

    /**
     * 文件大小（字节）
     */
    private Long fileSize;

    /**
     * 学科
     */
    private String subject;

    /**
     * 学段
     */
    private String gradeLevel;
}
