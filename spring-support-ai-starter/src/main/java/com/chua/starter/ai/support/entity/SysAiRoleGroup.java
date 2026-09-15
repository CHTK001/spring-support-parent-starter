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
    private Integer id;

    @TableField("sys_ai_role_group_name")
    private String name;

    @TableField("sys_ai_role_group_sort")
    private Integer sort;

    @TableField("sys_ai_role_group_status")
    private Integer status;

    @TableField("sys_ai_role_group_delete")
    @TableLogic
    private Integer delete;

    @TableField(fill = FieldFill.INSERT)
    private String createName;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
