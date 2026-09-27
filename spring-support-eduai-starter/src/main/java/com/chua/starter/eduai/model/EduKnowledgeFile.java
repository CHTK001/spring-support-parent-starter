package com.chua.starter.eduai.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.chua.starter.mybatis.pojo.SysBase;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 知识卡片附件条目实体
 *
 * @since 4.0.0.42
 * @author CH
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("edu_knowledge_file")
public class EduKnowledgeFile extends SysBase {

    /**
     * 附件条目标识
     */
    @TableId(type = IdType.AUTO)
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
     * 附件扩展名: png/jpg/pdf/docx 等
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
     * 归属用户 ID（服务端按登录态写入，客户端不可指定）
     */
    @TableField("knowledge_file_owner_user_id")
    private String ownerUserId;
}
