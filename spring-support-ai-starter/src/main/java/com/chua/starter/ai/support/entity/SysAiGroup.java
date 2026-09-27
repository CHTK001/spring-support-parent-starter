package com.chua.starter.ai.support.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.FieldFill;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * AI 模型分组实体。
 *
 * @author CH
 * @since 4.0.0
 */
@Data
@TableName("sys_ai_group")
public class SysAiGroup {

    /**
     * 分组主键
     */
    @TableId(value = "sys_ai_group_id", type = IdType.AUTO)
    /**
     * SYSAI分组标识
    */
    private Integer id;

    /**
     * 分组名称
     */
    @TableField("sys_ai_group_name")
    private String name; // 名称

    /**
     * 分组描述
     */
    @TableField("sys_ai_group_desc")
    private String description; // desc

    /**
     * 排序值
     */
    @TableField("sys_ai_group_sort")
    private Integer sort; // 排序

    /**
     * 状态：1 启用，0 禁用
     */
    @TableField("sys_ai_group_status")
    private Integer status; // 状态

    /**
     * 删除标记：0 未删除，1 已删除
     */
    @TableField("sys_ai_group_delete")
    private Integer deleted; // 删除

    /**
     * 创建时间
     */
    @TableField(fill = FieldFill.INSERT)
    /**
     * 创建时间
    */
    private LocalDateTime createTime;

    /**
     * 创建人
     */
    @TableField(fill = FieldFill.INSERT)
    /**
     * 创建by
    */
    private String createBy;

    /**
     * 更新时间
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    /**
     * 更新时间
    */
    private LocalDateTime updateTime;

    /**
     * 更新人
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    /**
     * 更新by
    */
    private String updateBy;
}

