package com.chua.starter.ai.support.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 模拟人生-世界NPC实体，对应表 {@code sys_ai_world_npc}。
 *
 * <p>世界 NPC 复用角色库 {@code sys_ai_role}（人设/头像/系统提示词），本表记录它
 * 在当前世界的"身份"：所在场景、世界内昵称、模型/温度覆盖、人设补充、是否参与
 * 推进与自动互动。一个角色可在多个世界各挂一个 NPC 实例。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Data
@TableName("sys_ai_world_npc")
public class SysAiWorldNpc {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 世界ID */
    @TableField("world_id")
    private Long worldId;

    /** 角色ID（sys_ai_role 主键） */
    @TableField("role_id")
    private Integer roleId;

    /** 当前所在场景ID */
    @TableField("scene_id")
    private Long sceneId;

    /** 世界内昵称（覆盖角色名） */
    @TableField("nickname")
    private String nickname;

    /** 世界内头像覆盖（可空） */
    @TableField("avatar")
    private String avatar;

    /** 模型覆盖（选填） */
    @TableField("model_override")
    private String modelOverride;

    /** 温度覆盖（选填） */
    @TableField("temperature")
    private Double temperature;

    /** 人设补充/覆盖（可空） */
    @TableField("persona")
    private String persona;

    /** 是否参与推进与自动互动 1是0否 */
    @TableField("is_active")
    private Boolean isActive;

    /** 排序 */
    @TableField("sort")
    private Integer sort;

    /** 归属用户 */
    @TableField("user_id")
    private String userId;

    @TableField("create_time")
    private LocalDateTime createTime;

    @TableField("update_time")
    private LocalDateTime updateTime;
}