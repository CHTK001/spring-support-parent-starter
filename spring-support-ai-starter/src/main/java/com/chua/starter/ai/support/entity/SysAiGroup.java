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
 */
@Data
@TableName("sys_ai_group")
public class SysAiGroup {

    /**
     * 分组主键
     */
    @TableId(value = "sys_ai_group_id", type = IdType.AUTO)
    /** SYSAI分组ID */
    private Integer id;

    /**
     * 分组名称
     */
    @TableField("sys_ai_group_name")
    private String name;

    /**
     * 分组描述
     */
    @TableField("sys_ai_group_desc")
    private String desc;

    /**
     * 排序值
     */
    @TableField("sys_ai_group_sort")
    private Integer sort;

    /**
     * 状态：1 启用，0 禁用
     */
    @TableField("sys_ai_group_status")
    private Integer status;

    /**
     * 删除标记：0 未删除，1 已删除
     */
    @TableField("sys_ai_group_delete")
    private Integer delete;

    /**
     * 创建时间
     */
    @TableField(fill = FieldFill.INSERT)
    /** Create时间 */
    private LocalDateTime createTime;

    /**
     * 创建人
     */
    @TableField(fill = FieldFill.INSERT)
    /** CreateBY */
    private String createBy;

    /**
     * 更新时间
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    /** Update时间 */
    private LocalDateTime updateTime;

    /**
     * 更新人
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    /** UpdateBY */
    private String updateBy;
}

