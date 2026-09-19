package com.chua.starter.ai.support.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 微信 机器人 聊天消息记录。
 *
 * <p>记录 Bot 与微信用户的往来消息：IN 为用户发往 Bot，
 * 出 为 机器人（AI 或主动）发往用户。聊天框据此展示历史记录。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Data
@TableName("sys_ai_bot_message")
public class SysAiBotMessage {

    /**
     * 消息 标识
     */
    @TableId(value = "sys_ai_bot_message_id", type = IdType.AUTO)
    private Long id; // 标识

    /**
     * 机器人 标识（关联绑定表）
     */
    @TableField("sys_ai_bot_message_bot_id")
    private String botId; // 机器人标识

    /**
     * 方向：入（用户→机器人）/ 出（机器人→用户）
     */
    @TableField("sys_ai_bot_message_direction")
    private String direction; // direction

    /**
     * 微信用户 标识（来源）
     */
    @TableField("sys_ai_bot_message_from_user")
    private String fromUser; // 从用户

    /**
     * 消息内容
     */
    @TableField("sys_ai_bot_message_content")
    private String content; // 内容

    /**
     * 创建时间
     */
    @TableField("sys_ai_bot_message_create_time")
    private LocalDateTime createTime; // 创建时间
}
