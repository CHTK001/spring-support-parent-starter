package com.chua.starter.eduai.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import com.chua.starter.mybatis.pojo.SysBase;

/**
 * 知识点实体
 *
 * @since 4.0.0.42
 * @author CH
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("edu_knowledge_point")
public class EduKnowledgePoint extends SysBase {

    /**
     * 知识点标识
     */
    @TableId(type = IdType.AUTO)
    /**
     * knowledgepointid
    */
    private Long knowledgePointId;

    /**
     * 知识点名称
     */
    private String knowledgePointName;

    /**
     * 知识点编码
     */
    private String knowledgePointCode;

    /**
     * 父知识点标识
     */
    private Long knowledgePointParentId;

    /**
     * 学科
     */
    private String knowledgePointSubject;

    /**
     * 学段
     */
    private String knowledgePointGradeLevel;

    /**
     * 年级
     */
    private String knowledgePointGrade;

    /**
     * 知识点描述
     */
    private String knowledgePointDescription;

    /**
     * 内容类型: TEXT/FILE
     */
    private String knowledgePointContentType;

    /**
     * 文本内容（TEXT 类型时使用）
     */
    private String knowledgePointContent;

    /**
     * 附件原始文件名
     */
    private String knowledgePointFileName;

    /**
     * 附件存储相对路径
     */
    private String knowledgePointFilePath;

    /**
     * 附件扩展名: png/jpg/pdf/docx 等
     */
    private String knowledgePointFileType;

    /**
     * 附件字节数
     */
    private Long knowledgePointFileSize;

    /**
     * 向量嵌入JSON
     */
    private String knowledgePointEmbedding;

    /**
     * 排序号
     */
    private Integer knowledgePointSortOrder;

    /**
     * 状态
     */
    private Integer knowledgePointStatus;

    /**
     * 归属用户 ID（服务端按登录态写入，客户端不可指定）
     */
    @TableField("knowledge_point_owner_user_id")
    private String ownerUserId;
}
