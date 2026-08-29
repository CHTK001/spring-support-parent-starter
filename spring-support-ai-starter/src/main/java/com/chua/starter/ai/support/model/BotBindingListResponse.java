package com.chua.starter.ai.support.model;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * Bot 绑定列表响应。
 *
 * @author CH
 * @since 4.0.0.42
 */
@Data
public class BotBindingListResponse {

    /**
     * 绑定 ID
     */
    private Long id;

    /**
     * 归属用户 ID（可为空，表示未分配用户）
     */
    private Long userId;

    /**
     * 归属用户昵称（来自绑定记录 nickname，扫码场景为微信 openid）
     */
    private String userName;

    /**
     * Bot 唯一标识
     */
    private String botId;

    /**
     * 平台类型
     */
    private String platform;

    /**
     * Bot 昵称
     */
    private String nickname;

    /**
     * 头像地址
     */
    private String avatarUrl;

    /**
     * 独立网关端口号
     */
    private Integer gatewayPort;

    /**
     * 状态：online / offline / expired
     */
    private String status;

    /**
     * 绑定来源
     */
    private String bindSource;

    /**
     * 绑定的 AI 模型（用于微信聊天时 AI 回复）
     */
    private String model;

    /**
     * 是否启用深度思考（默认 false）
     */
    private Boolean thinking;

    /**
     * 深度思考强度（low / medium / high）
     */
    private String thinkingEffort;

    /**
     * 系统提示词（AI 回复时注入的 system prompt）
     */
    private String systemPrompt;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 最后活跃时间
     */
    private LocalDateTime lastActiveTime;
}
