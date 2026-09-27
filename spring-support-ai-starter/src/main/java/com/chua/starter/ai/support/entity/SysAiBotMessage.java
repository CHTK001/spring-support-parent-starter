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
     * 微信用户 标识（消息作者）
     */
    @TableField("sys_ai_bot_message_from_user")
    private String fromUser; // 从用户

    /**
     * 会话标识（上下文隔离键）。
     *
     * <p>群聊为群 openid（chatId），私聊为用户 openid（与 fromUser 相同）。
     * 同一 chatId 的消息构成一个独立上下文。</p>
     */
    @TableField("sys_ai_bot_message_chat_id")
    private String chatId;

    /**
     * 是否来自群聊：1=群消息，0=私聊。
     */
    @TableField("sys_ai_bot_message_from_group")
    private Boolean fromGroup;

    /**
     * 消息内容
     */
    @TableField("sys_ai_bot_message_content")
    private String content; // 内容

    /**
     * 会话上下文令牌（仅入站 IN 消息携带并持久化）。
     *
     * <p>微信 iLink 入站消息携带 context_token，回复 / 主动发送时必须原样回传。
     * 该令牌仅在内存中会随后端重启丢失，故随 IN 消息落库；重启后发送时回查
     * 最近一条 context_token 回填，避免服务器返回 ret=-2「prepare failed」。</p>
     */
    @TableField("sys_ai_bot_message_context_token")
    private String contextToken;

    /**
     * 幂等去重键（仅入站 IN 消息写入）。
     *
     * <p>有稳定平台消息 ID 时为 {@code ID:botId|chatId|msgId}；
     * 平台不提供稳定 ID 时为 {@code FP:botId|chatId|时间窗|内容指纹}。
     * 该列加唯一索引，多实例同时收到同一条消息时，仅第一个插入成功的实例会处理并回复，
     * 其余实例触发唯一键冲突并跳过，保证 Bot 不重复回复。</p>
     */
    @TableField("sys_ai_bot_message_dedup_key")
    private String dedupKey;

    /**
     * 创建时间
     */
    @TableField("sys_ai_bot_message_create_time")
    private LocalDateTime createTime; // 创建时间

    /**
     * 归属登录用户 标识
     *
     * <p>随所属 机器人 绑定记录的归属写入（owner_key 的 {@code u:<userId>} 去掉前缀），
     * 未登录（设备指纹）流量保持为空。</p>
     */
    @TableField("sys_ai_bot_message_user_id")
    private String userId; // 归属用户
}
