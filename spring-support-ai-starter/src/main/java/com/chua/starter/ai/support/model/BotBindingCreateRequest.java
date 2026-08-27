package com.chua.starter.ai.support.model;

import lombok.Data;

/**
 * Bot 绑定创建请求。
 *
 * @author CH
 * @since 4.0.0.42
 */
@Data
public class BotBindingCreateRequest {

    /**
     * Bot 唯一标识
     */
    private String botId;

    /**
     * 登录凭证 Token
     */
    private String botToken;

    /**
     * 平台类型
     */
    private String platform;

    /**
     * Bot 昵称
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
}
