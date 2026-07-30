package com.chua.starter.ai.support.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 分组-模型关联实体。
 *
 * @author CH
 */
@Data
@TableName("sys_ai_group_model")
public class SysAiGroupModel {

    /**
     * 主键
     */
    @TableId(type = IdType.AUTO)
    private Integer sysAiGroupModelId;

    /**
     * 分组 ID
     */
    private Integer sysAiGroupId;

    /**
     * 模块 ID
     */
    private Integer sysAiModuleId;

    /**
     * 创建时间
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
