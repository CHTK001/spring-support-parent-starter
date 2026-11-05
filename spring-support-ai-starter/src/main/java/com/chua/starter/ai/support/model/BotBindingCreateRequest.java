package com.chua.starter.ai.support.model;

/**
 * 机器人 绑定创建请求。
 *
 * @author CH
 * @since 4.0.0.42
 */
public record BotBindingCreateRequest(
        /**
         * 机器人 唯一标识
         */
        String botId,
        /**
         * 登录凭证 令牌
         */
        String botToken,
        /**
         * 平台类型
         */
        String platform,
        /**
         * 机器人 昵称
         */
        String nickname,
        /**
         * 独立网关端口号
         */
        Integer gatewayPort,
        /**
         * 状态
         */
        String status,
        /**
         * 绑定来源
         */
        String bindSource,
        /**
         * 绑定的 AI 模型（用于微信聊天时 AI 回复）
         */
        String model,
        /**
         * 是否启用深度思考（默认 false，不开启）
         */
        Boolean thinking,
        /**
         * 深度思考强度（low / medium / high）
         */
        String thinkingEffort,
        /**
         * 系统提示词（AI 回复时注入的 系统 提示符）
         */
        String systemPrompt,
        /**
         * 是否记录 AI 用量（令牌/费用），机器人 专属开关，默认开启
         */
        Boolean recordUsage,
        /**
         * 是否仅回复 @ 了 Bot 的消息（群聊场景）
         */
        Boolean mentionOnly,
        /**
         * 是否启用随机回复
         */
        Boolean randomReplyEnabled,
        /**
         * 随机回复携带的上下文条数
         */
        Integer randomContextCount,
        /**
         * 随机回复命中概率百分比 [0, 100]
         */
        Double randomProbability
) {
}
