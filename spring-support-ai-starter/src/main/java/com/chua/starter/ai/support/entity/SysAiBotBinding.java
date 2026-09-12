package com.chua.starter.ai.support.entity;

import com.chua.starter.mybatis.pojo.SysBase;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 微信 iLink Bot 绑定信息实体。
 *
 * <p>记录扫码绑定的 Bot 凭证、连接状态与元数据，
 * 支持同一系统绑定多个 Bot 实例。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_ai_bot_binding")
public class SysAiBotBinding extends SysBase {

    /**
     * 绑定 ID
     */
    @TableId(value = "sys_ai_bot_binding_id", type = IdType.AUTO)
    private Long id;

    /**
     * 关联用户 ID
     */
    @TableField("sys_ai_bot_binding_user_id")
    private Long userId;

    /**
     * 归属标识，用于绑定资源的所有权过滤。
     *
     * <p>登录用户为 {@code u:<userId>}；未登录（匿名）为 {@code fp:<浏览器指纹>}
     * （取自请求头 {@code x-req-fingerprint} / {@code X-Request-Fingerprint}）。
     * 为空表示未归属（历史数据）。</p>
     */
    @TableField("sys_ai_bot_binding_owner_key")
    private String ownerKey;

    /**
     * Bot 唯一标识（iLink 分配的 botId）
     */
    @TableField("sys_ai_bot_binding_bot_id")
    private String botId;

    /**
     * 登录凭证 Token
     */
    @TableField("sys_ai_bot_binding_bot_token")
    private String botToken;

    /**
     * 平台类型（wechat / wecom）
     */
    @TableField("sys_ai_bot_binding_platform")
    private String platform;

    /**
     * Bot 昵称（来自微信资料）
     */
    @TableField("sys_ai_bot_binding_nickname")
    private String nickname;

    /**
     * 头像地址
     */
    @TableField("sys_ai_bot_binding_avatar_url")
    private String avatarUrl;

    /**
     * 独立网关端口号
     */
    @TableField("sys_ai_bot_binding_gateway_port")
    private Integer gatewayPort;

    /**
     * 状态：online / offline / expired
     */
    @TableField("sys_ai_bot_binding_status")
    private String status;

    /**
     * 绑定来源（qrcode_scan / manual_import）
     */
    @TableField("sys_ai_bot_binding_bind_source")
    private String bindSource;

    /**
     * 绑定的 AI 模型（用于微信聊天时 AI 回复）
     */
    @TableField("sys_ai_bot_binding_model")
    private String model;

    /**
     * 是否启用深度思考（默认关闭）
     */
    @TableField("sys_ai_bot_binding_thinking")
    private Boolean thinking;

    /**
     * 深度思考强度（low / medium / high，默认 high）
     */
    @TableField("sys_ai_bot_binding_thinking_effort")
    private String thinkingEffort;

    /**
     * 系统提示词（AI 回复时注入的 system prompt）
     */
    @TableField("sys_ai_bot_binding_system_prompt")
    private String systemPrompt;

    /**
     * 是否记录 AI 用量（token/费用），bot 专属开关，默认开启。
     * 关闭后该 bot 的 AI 调用不计入 usage 统计。
     */
    @TableField("sys_ai_bot_binding_record_usage")
    private Boolean recordUsage;

    /**
     * 最后活跃时间
     */
    @TableField("sys_ai_bot_binding_last_active_time")
    private LocalDateTime lastActiveTime;
}
