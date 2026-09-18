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
* @since 4.0.0.42
* @author CH
 */
@Data
@TableName("sys_ai_knowledge_library")
public class SysAiKnowledgeLibrary {

    /**
    * 知识库主键 标识
    */
    @TableId(value = "sys_ai_knowledge_library_id", type = IdType.AUTO)
    /** sysaiknowledgelibraryid */
    private Integer id;

    /**
    * 知识库名称
    */
    @TableField("sys_ai_knowledge_library_name")
    private String name; // 名称

    /**
    * 知识库描述
    */
    @TableField("sys_ai_knowledge_library_description")
    private String description; // description

    /**
    * 状态：1 启用，0 停用
    */
    @TableField("sys_ai_knowledge_library_status")
    private Integer status; // 状态

    /**
    * 创建时间
    */
    @TableField("create_time")
    /** 创建时间 */
    private LocalDateTime createTime;

    /**
    * 更新时间
    */
    @TableField("update_time")
    /** 更新时间 */
    private LocalDateTime updateTime;
}

