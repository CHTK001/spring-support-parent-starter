package com.chua.starter.ai.support.model;

import java.time.LocalDateTime;

/**
 * 机器人 绑定列表响应。
 *
 * @param id                  绑定 标识
 * @param userId              归属用户 标识（可为空，表示未分配用户）
 * @param userName            归属用户昵称（来自绑定记录 nickname，扫码场景为微信 openid）
 * @param botId               机器人 唯一标识
 * @param platform            平台类型
 * @param nickname            机器人 昵称
 * @param avatarUrl           头像地址
 * @param gatewayPort         独立网关端口号
 * @param status              状态：online / offline / expired
 * @param bindSource          绑定来源
 * @param model               绑定的 AI 模型（用于微信聊天时 AI 回复）
 * @param thinking            是否启用深度思考（默认 false）
 * @param thinkingEffort      深度思考强度（low / medium / high）
 * @param systemPrompt        系统提示词（AI 回复时注入的 系统 提示符）
 * @param recordUsage         是否记录 AI 用量（令牌/费用），机器人 专属开关，默认开启
 * @param mentionOnly         是否仅回复 @ 了 Bot 的消息（群聊场景）
 * @param randomReplyEnabled  是否启用随机回复
 * @param randomContextCount  随机回复携带的上下文条数
 * @param randomProbability   随机回复命中概率百分比 [0, 100]
 * @param createTime          创建时间
 * @param lastActiveTime      最后活跃时间
 * @author CH
 * @since 4.0.0.42
 */
public record BotBindingListResponse(
        Long id,
        Long userId,
        String userName,
        String botId,
        String platform,
        String nickname,
        String avatarUrl,
        Integer gatewayPort,
        String status,
        String bindSource,
        String model,
        Boolean thinking,
        String thinkingEffort,
        String systemPrompt,
        Boolean recordUsage,
        Boolean mentionOnly,
        Boolean randomReplyEnabled,
        Integer randomContextCount,
        Double randomProbability,
        LocalDateTime createTime,
        LocalDateTime lastActiveTime) {
}
