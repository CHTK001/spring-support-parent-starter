package com.chua.starter.ai.support.model;

import lombok.Data;

/**
 * 机器人 绑定创建请求。
 *
 * @author CH
 * @since 4.0.0.42
 */
@Data
public class BotBindingCreateRequest {

    /**
     * 机器人 唯一标识
     */
    private String botId;

    /**
     * 登录凭证 令牌
     */
    private String botToken;

    /**
     * 平台类型
     */
    private String platform;

    /**
     * 机器人 昵称
     */
    private String nickname;

    /**
     * 独立网关端口号
     */
    private Integer gatewayPort;

    /**
     * 状态
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
     * 是否启用深度思考（默认 false，不开启）
     */
    private Boolean thinking;

    /**
     * 深度思考强度（low / medium / high）
     */
    private String thinkingEffort;

    /**
     * 系统提示词（AI 回复时注入的 系统 提示符）
     */
    private String systemPrompt;

    /**
     * 是否记录 AI 用量（令牌/费用），机器人 专属开关，默认开启
     */
    private Boolean recordUsage;
}
