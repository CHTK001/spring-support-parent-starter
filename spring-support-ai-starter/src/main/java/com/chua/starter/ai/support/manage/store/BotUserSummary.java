package com.chua.starter.ai.support.manage.store;

/**
 * 绑定 机器人 的用户聚合摘要（管理员视角）。
 *
 * <p>按 {link SysAiBotBinding#getUserId()}(userId，缺失时用昵称) 分组，
 * 便于管理员查看某用户绑定了多少 机器人、多少在线。</p>
 *
 * @param owner          用户归属 Key（优先 用户标识，缺失时用昵称；两者皆无则 "unassigned"）
 * @param nickname       展示昵称（取该用户最新一条绑定的 nickname；无则显示 owner）
 * @param botCount       该用户绑定的 机器人 总数
 * @param onlineCount    该用户在线 机器人 数
 * @param offlineCount   该用户过期/离线 机器人 数
 * @param lastActiveTime 最后活跃时间（毫秒，该用户任一 机器人 的最新活跃）
 * @author CH
 * @since 4.0.0.42
 */
public record BotUserSummary(
        String owner,
        String nickname,
        long botCount,
        long onlineCount,
        long offlineCount,
        Long lastActiveTime) {
}
