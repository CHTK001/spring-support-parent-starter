package com.chua.starter.ai.support.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 微信 Bot 聊天消息记录。
 *
 * <p>记录 Bot 与微信用户的往来消息：IN 为用户发往 Bot，
 * OUT 为 Bot（AI 或主动）发往用户。聊天框据此展示历史记录。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Data
@TableName("sys_ai_bot_message")
public class SysAiBotMessage {

    /**
     * 消息 ID
     */
    @TableId(value = "sys_ai_bot_message_id", type = IdType.AUTO)
    private Long id;

    /**
     * Bot ID（关联绑定表）
     */
    @TableField("sys_ai_bot_message_bot_id")
    private String botId;

    /**
     * 方向：IN（用户→bot）/ OUT（bot→用户）
     */
    @TableField("sys_ai_bot_message_direction")
    private String direction;

    /**
     * 微信用户 ID（来源）
     */
    @TableField("sys_ai_bot_message_from_user")
    private String fromUser;

    /**
     * 消息内容
     */
    @TableField("sys_ai_bot_message_content")
    private String content;

    /**
     * 创建时间
     */
    @TableField("sys_ai_bot_message_create_time")
    private LocalDateTime createTime;
}