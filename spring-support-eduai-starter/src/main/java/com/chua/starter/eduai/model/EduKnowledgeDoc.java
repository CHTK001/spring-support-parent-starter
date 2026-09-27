package com.chua.starter.eduai.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import com.chua.starter.mybatis.pojo.SysBase;

/**
 * 知识库文档实体
 *
 * @since 4.0.0.42
 * @author CH
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("edu_knowledge_doc")
public class EduKnowledgeDoc extends SysBase {

    /**
     * 文档标识
     */
    @TableId(type = IdType.AUTO)
    /**
     * 知识docid
    */
    private Long knowledgeDocId;

    /**
     * 文档名称
     */
    private String knowledgeDocName;

    /**
     * 文档类型
     */
    private String knowledgeDocType;

    /**
     * 文件存储路径
     */
    private String knowledgeDocFilePath;

    /**
     * 文件大小
     */
    private Long knowledgeDocFileSize;

    /**
     * 向量存储标识
     */
    private String knowledgeDocVectorStoreId;

    /**
     * 学科
     */
    private String knowledgeDocSubject;

    /**
     * 学段
     */
    private String knowledgeDocGradeLevel;

    /**
     * 状态
     */
    private Integer knowledgeDocStatus;

    /**
     * 归属用户 ID（服务端按登录态写入，客户端不可指定）
     */
    @TableField("knowledge_doc_owner_user_id")
    private String ownerUserId;
}
