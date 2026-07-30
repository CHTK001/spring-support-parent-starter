package com.chua.starter.ai.support.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * AI 模型分组实体。
 *
 * @author CH
 */
@Data
@TableName("sys_ai_group")
public class SysAiGroup {

    /**
     * 分组主键
     */
    @TableId(type = IdType.AUTO)
    private Integer sysAiGroupId;

    /**
     * 分组名称
     */
    private String sysAiGroupName;

    /**
     * 分组描述
     */
    private String sysAiGroupDesc;

    /**
     * 排序值
     */
    private Integer sysAiGroupSort;

    /**
     * 状态：1 启用，0 禁用
     */
    private Integer sysAiGroupStatus;

    /**
     * 删除标记：0 未删除，1 已删除
     */
    private Integer sysAiGroupDelete;

    /**
     * 创建时间
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /**
     * 创建人
     */
    @TableField(fill = FieldFill.INSERT)
    private String createBy;

    /**
     * 更新时间
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    /**
     * 更新人
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private String updateBy;
}
