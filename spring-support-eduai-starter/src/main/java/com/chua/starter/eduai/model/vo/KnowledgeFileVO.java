package com.chua.starter.eduai.model.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 知识卡片附件条目VO
 *
 * @since 4.0.0.42
 * @author CH
 */
@Data
public class KnowledgeFileVO {

    /**
     * 附件条目标识
     */
    private Long knowledgeFileId;

    /**
     * 所属知识点（卡片）标识
     */
    private Long knowledgePointId;

    /**
     * 附件原始文件名
     */
    private String knowledgeFileName;

    /**
     * 附件存储相对路径
     */
    private String knowledgeFilePath;

    /**
     * 附件扩展名
     */
    private String knowledgeFileType;

    /**
     * 附件字节数
     */
    private Long knowledgeFileSize;

    /**
     * 排序号
     */
    private Integer knowledgeFileSortOrder;

    /**
     * 状态: 0-停用 1-启用
     */
    private Integer knowledgeFileStatus;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;
}
