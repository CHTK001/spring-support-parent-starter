package com.chua.starter.ai.support.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 模拟-场景对话消息实体，对应表 {@code sys_ai_world_message}。
 *
 * <p>与酒馆群消息同构：在「世界 → 场景」维度下沉用户与 AI 的逐条对话；
 * 历史按 (worldId, sceneId) 拉取，作为多角色对话的上文。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Data
@TableName("sys_ai_world_message")
public class SysAiWorldMessage {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 世界ID */
    @TableField("world_id")
    private Long worldId;

    /** 场景ID */
    @TableField("scene_id")
    private Long sceneId;

    /** 发言者类型 user/ai */
    @TableField("speaker_type")
    private String speakerType;

    /** 发言者ID（user=登录用户名, ai=角色ID） */
    @TableField("speaker_id")
    private String speakerId;

    /** 发言者显示名 */
    @TableField("speaker_name")
    private String speakerName;

    /** 消息内容 */
    @TableField("content")
    private String content;

    /** 本条AI回复使用的模型 */
    @TableField("model_name")
    private String modelName;

    /** 本条用户消息@的角色ID */
    @TableField("at_role_id")
    private Integer atRoleId;

    /** 归属用户 */
    @TableField("user_id")
    private String userId;

    @TableField("create_time")
    private LocalDateTime createTime;
}