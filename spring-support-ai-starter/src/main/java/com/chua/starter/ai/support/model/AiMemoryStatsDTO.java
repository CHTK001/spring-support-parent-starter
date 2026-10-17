package com.chua.starter.ai.support.model;

import java.io.Serializable;

/**
 * 记忆体容量统计 —— 当前登录用户的活跃记忆条数与容量上限。
 *
 * <p>供会话顶部「上下文管理」面板与设置页展示「已用/上限」，上限取自 {@code memory} 分组
 * 的 {@code memory_max_per_user}，改完下一轮即生效。</p>
 *
 * @param userId     归属用户
 * @param active     活跃记忆条数
 * @param capacity   单用户活跃记忆上限（0=不限制）
 * @param recallable 可召回条数（参与注入的活跃记忆，不含过期）
 * @since 4.0.0.42
 * @author CH
 */
public record AiMemoryStatsDTO(
        String userId,
        Integer active,
        Integer capacity,
        Integer recallable) implements Serializable {

    private static final long serialVersionUID = 1L;
}
