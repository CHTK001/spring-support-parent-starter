package com.chua.starter.eduai.model.vo;

import java.time.LocalDateTime;

/**
 * 知识卡片附件条目VO
 *
 * @param knowledgeFileId         附件条目标识
 * @param knowledgePointId        所属知识点（卡片）标识
 * @param knowledgeFileName       附件原始文件名
 * @param knowledgeFilePath       附件存储相对路径
 * @param knowledgeFileType       附件扩展名
 * @param knowledgeFileSize       附件字节数
 * @param knowledgeFileSortOrder  排序号
 * @param knowledgeFileStatus     状态: 0-停用 1-启用
 * @param createTime              创建时间
 * @since 4.0.0.42
 * @author CH
 */
public record KnowledgeFileVO(
        Long knowledgeFileId,
        Long knowledgePointId,
        String knowledgeFileName,
        String knowledgeFilePath,
        String knowledgeFileType,
        Long knowledgeFileSize,
        Integer knowledgeFileSortOrder,
        Integer knowledgeFileStatus,
        LocalDateTime createTime) {
}
