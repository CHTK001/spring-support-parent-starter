package com.chua.starter.eduai.model.dto;

/**
 * 知识点保存DTO
 *
 * @param knowledgePointId 知识点标识（更新时必填）
 * @param knowledgePointName 知识点名称
 * @param knowledgePointCode 知识点编码
 * @param knowledgePointParentId 父知识点标识，0表示根节点
 * @param knowledgePointSubject 所属学科
 * @param knowledgePointGradeLevel 学段
 * @param knowledgePointGrade 年级
 * @param knowledgePointDescription 知识点描述
 * @param knowledgePointSortOrder 排序号
 * @param knowledgePointStatus 状态（0-停用 1-启用）
 * @param knowledgePointContentType 内容类型: TEXT/FILE
 * @param knowledgePointContent 文本内容
 * @param knowledgePointFileName 附件原始文件名
 * @param knowledgePointFilePath 附件存储相对路径
 * @param knowledgePointFileType 附件扩展名
 * @param knowledgePointFileSize 附件字节数
 *
 * @since 4.0.0.42
 * @author CH
 */
public record KnowledgePointSaveDTO(
        Long knowledgePointId,
        String knowledgePointName,
        String knowledgePointCode,
        Long knowledgePointParentId,
        String knowledgePointSubject,
        String knowledgePointGradeLevel,
        String knowledgePointGrade,
        String knowledgePointDescription,
        Integer knowledgePointSortOrder,
        Integer knowledgePointStatus,
        String knowledgePointContentType,
        String knowledgePointContent,
        String knowledgePointFileName,
        String knowledgePointFilePath,
        String knowledgePointFileType,
        Long knowledgePointFileSize
) {
}
