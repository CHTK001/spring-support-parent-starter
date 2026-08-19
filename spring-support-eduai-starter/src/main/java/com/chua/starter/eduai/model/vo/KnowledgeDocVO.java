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
     * 文档ID
     */
    private Long id;

    /**
     * 文档名称
     */
    private String name;

    /**
     * 文档类型
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
     * 向量存储ID
     */
    private String vectorStoreId;

    /**
     * 学科
     */
    private String subject;

    /**
     * 学段
     */
    private String gradeLevel;

    /**
     * 状态（0-处理中 1-已索引 2-失败）
     */
    private Integer status;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;
}
