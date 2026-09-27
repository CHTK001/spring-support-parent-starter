package com.chua.starter.ai.support.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 酒馆群消息实体，对应表 {@code sys_ai_tavern_message}。
 *
 * <p>发言者区分：user=群主(真人)，ai=群成员(角色)。群聊历史按发言者拼接，
 * 让被回复的成员知道群里谁说过什么。</p>
 *
 * @author CH
 */
@Data
@TableName("sys_ai_tavern_message")
public class SysAiTavernMessage {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 群ID */
    @TableField("group_id")
    private Long groupId;

    /** 发言者类型 user / ai */
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
