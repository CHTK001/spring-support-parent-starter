package com.chua.starter.ai.support.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.FieldFill;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * AI 知识库实体，对应表 {@code sys_ai_knowledge_library}。
 *
 * @author CH
 * @since 4.0.0.42
 */
@Data
@TableName("sys_ai_knowledge_library")
public class SysAiKnowledgeLibrary {

    /**
     * 知识库主键 ID
     */
    @TableId(type = IdType.AUTO)
    private Integer sysAiKnowledgeLibraryId;

    /**
     * 知识库名称
     */
    @TableField("sys_ai_knowledge_library_name")
    private String sysAiKnowledgeLibraryName;

    /**
     * 知识库描述
     */
    @TableField("sys_ai_knowledge_library_description")
    private String sysAiKnowledgeLibraryDescription;

    /**
     * 状态：1 启用，0 停用
     */
    @TableField("sys_ai_knowledge_library_status")
    private Integer sysAiKnowledgeLibraryStatus;

    /**
     * 创建时间
     */
    @TableField("create_time")
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    @TableField("update_time")
    private LocalDateTime updateTime;
}

