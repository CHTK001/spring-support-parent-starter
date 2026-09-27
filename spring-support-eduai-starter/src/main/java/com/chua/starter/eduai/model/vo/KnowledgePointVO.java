package com.chua.starter.eduai.model.vo;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 知识点VO
 *
 * @param knowledgePointId 知识点标识
 * @param knowledgePointName 知识点名称
 * @param knowledgePointCode 知识点编码
 * @param knowledgePointParentId 父知识点标识
 * @param knowledgePointSubject 所属学科
 * @param knowledgePointGradeLevel 学段
 * @param knowledgePointGrade 年级
 * @param knowledgePointDescription 知识点描述
 * @param knowledgePointSortOrder 排序号
 * @param knowledgePointStatus 状态
 * @param knowledgePointContentType 内容类型: TEXT/FILE
 * @param knowledgePointContent 文本内容
 * @param knowledgePointFileName 附件原始文件名
 * @param knowledgePointFilePath 附件存储相对路径
 * @param knowledgePointFileType 附件扩展名
 * @param knowledgePointFileSize 附件字节数
 * @param createTime 创建时间
 * @param children 子知识点列表
 *
 * @since 4.0.0.42
 * @author CH
 */
public record KnowledgePointVO(
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
        Long knowledgePointFileSize,
        LocalDateTime createTime,
        List<KnowledgePointVO> children
) {
    /**
     * 规范构造器：对子知识点列表做防御性拷贝。
     *
     * <p>value class 前置条件——集合组件必须深不可变。子知识点列表允许为 null
     * （叶子节点由实体转换时显式传 null），此处保留 null 语义，仅在非空时拷贝。</p>
     *
     * @param knowledgePointId 知识点标识
     * @param knowledgePointName 知识点名称
     * @param knowledgePointCode 知识点编码
     * @param knowledgePointParentId 父知识点标识
     * @param knowledgePointSubject 所属学科
     * @param knowledgePointGradeLevel 学段
     * @param knowledgePointGrade 年级
     * @param knowledgePointDescription 知识点描述
     * @param knowledgePointSortOrder 排序号
     * @param knowledgePointStatus 状态
     * @param knowledgePointContentType 内容类型: TEXT/FILE
     * @param knowledgePointContent 文本内容
     * @param knowledgePointFileName 附件原始文件名
     * @param knowledgePointFilePath 附件存储相对路径
     * @param knowledgePointFileType 附件扩展名
     * @param knowledgePointFileSize 附件字节数
     * @param createTime 创建时间
     * @param children 子知识点列表，可为 null
     */
    public KnowledgePointVO {
        children = children == null ? null : List.copyOf(children);
    }

    /**
     * 用子知识点列表替换当前 VO 的 children 组件
     *
     * @param children 子知识点列表
     * @return 携带子列表的新 VO
     */
    public KnowledgePointVO withChildren(List<KnowledgePointVO> children) {
        return new KnowledgePointVO(knowledgePointId, knowledgePointName, knowledgePointCode,
                knowledgePointParentId, knowledgePointSubject, knowledgePointGradeLevel,
                knowledgePointGrade, knowledgePointDescription, knowledgePointSortOrder,
                knowledgePointStatus, knowledgePointContentType, knowledgePointContent,
                knowledgePointFileName, knowledgePointFilePath, knowledgePointFileType,
                knowledgePointFileSize, createTime, children);
    }
}
