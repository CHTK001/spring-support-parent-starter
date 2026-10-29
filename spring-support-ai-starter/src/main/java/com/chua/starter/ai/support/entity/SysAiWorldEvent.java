package com.chua.starter.ai.support.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 模拟人生-世界事件时间线实体，对应表 {@code sys_ai_world_event}。
 *
 * <p>世界动态的持久化事件流：推进（advance，生成一段「世界动态」旁白）、
 * 对话（chat，场景内发生了值得记录的互动）、系统（system）。按时间流水展示，
 * 是前端主时间线的数据来源。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Data
@TableName("sys_ai_world_event")
public class SysAiWorldEvent {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 世界ID */
    @TableField("world_id")
    private Long worldId;

    /** 事件发生场景（可空） */
    @TableField("scene_id")
    private Long sceneId;

    /** 类型：advance 世界动态 / chat 对话 / system 系统 */
    @TableField("event_type")
    private String eventType;

    /** 参与角色ID（逗号分隔，sys_ai_world_npc.id） */
    @TableField("role_ids")
    private String roleIds;

    /** 事件标题 */
    @TableField("title")
    private String title;

    /** 事件正文 */
    @TableField("content")
    private String content;

    /** 归属用户 */
    @TableField("user_id")
    private String userId;

    @TableField("create_time")
    private LocalDateTime createTime;
}