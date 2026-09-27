package com.chua.starter.eduai.model.dto;

/**
 * 知识卡片附件绑定DTO（upload 成功后把相对路径挂到卡片）
 *
 * @param knowledgePointId 所属知识点（卡片）标识
 * @param knowledgeFileName 附件原始文件名
 * @param knowledgeFilePath 附件存储相对路径
 * @param knowledgeFileType 附件扩展名
 * @param knowledgeFileSize 附件字节数
 * @param knowledgeFileSortOrder 排序号
 *
 * @since 4.0.0.42
 * @author CH
 */
public record KnowledgeFileBindDTO(
        Long knowledgePointId,
        String knowledgeFileName,
        String knowledgeFilePath,
        String knowledgeFileType,
        Long knowledgeFileSize,
        Integer knowledgeFileSortOrder
) {
}
