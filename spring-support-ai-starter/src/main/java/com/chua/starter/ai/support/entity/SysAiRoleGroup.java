package com.chua.starter.ai.support.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 角色分组实体，对应表 sys_ai_role_group
 */
@Data
@TableName("sys_ai_role_group")
public class SysAiRoleGroup {

    @TableId(value = "sys_ai_role_group_id", type = IdType.AUTO)
    /**
     * ID
    */
    private Integer id;

    @TableField("sys_ai_role_group_name")
    /**
     * 名称
    */
    private String name;

    @TableField("sys_ai_role_group_sort")
    /**
     * 排序
    */
    private Integer sort;

    @TableField("sys_ai_role_group_status")
    /**
     * 状态
    */
    private Integer status;

    @TableField("sys_ai_role_group_delete")
    @TableLogic
    /**
     * 删除
    */
    private Integer deleted;

    @TableField(fill = FieldFill.INSERT)
    /**
     * 创建名称
    */
    private String createName;

    @TableField(fill = FieldFill.INSERT)
    /**
     * 创建时间
    */
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    /**
     * 更新时间
    */
    private LocalDateTime updateTime;
}
