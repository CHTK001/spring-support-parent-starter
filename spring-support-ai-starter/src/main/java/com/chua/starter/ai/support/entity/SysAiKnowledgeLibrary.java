package com.chua.starter.ai.support.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("sys_ai_knowledge_library")
public class SysAiKnowledgeLibrary {

    @TableId(type = IdType.AUTO)
    private Integer sysAiKnowledgeLibraryId;

    @TableField("name")
    private String sysAiKnowledgeLibraryName;

    @TableField("description")
    private String sysAiKnowledgeLibraryDescription;

    @TableField("status")
    private Integer sysAiKnowledgeLibraryStatus;

    @TableField("create_time")
    private LocalDateTime createTime;

    @TableField("update_time")
    private LocalDateTime updateTime;
}
